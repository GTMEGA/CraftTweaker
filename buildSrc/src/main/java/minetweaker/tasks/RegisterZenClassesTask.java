package minetweaker.tasks;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates a native registrar class. Finds all classes with a @ZenClass or
 * @ZenExpansion annotation and generates a class with a single static
 * getClasses(List<Class>) method.  Overrides existing files if they exist.
 * (handy for having a stub in the original source)
 *
 * @author Stan Hebben
 */
public class RegisterZenClassesTask extends DefaultTask {
	@InputDirectory
	public File inputDir;

	@OutputDirectory
	public File outputDir = null;

	public void setInputDir(File inputDir) {
		this.inputDir = inputDir;
	}

	public File getInputDir() {
		return this.inputDir;
	}

	public void setOutputDir(File inputDir) {
		this.outputDir = inputDir;
	}

	public File getOutputDir() {
		return this.outputDir;
	}

	@Input
	public String className;

	public void setClassName(String className) {
		this.className = className;
	}

	public String getClassName() {
		return this.className;
	}

	@TaskAction
	public void doTask() {
		if (outputDir == null) outputDir = inputDir;

		Map<String, List<String>> modOnly = new HashMap<>();
		List<String> classNames = new ArrayList<String>();
		List<OnRegisterMethod> onRegisterMethods = new ArrayList<OnRegisterMethod>();
		iterate(inputDir, null, classNames, modOnly, onRegisterMethods);

		String fullClassName = className.replace('.', '/');

		// generate class
		ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		output.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, fullClassName, null, "java/lang/Object", null);

		MethodVisitor method = output.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getClasses", "(Ljava/util/List;)V", null, null);
		method.visitCode();

		class LoadedModsHelper {
			List<String> currentLoadedMods = null;
			Label currentLoadedModEnd = null;

			void update(String clsName) {
				output.newClass("asd");
				List<String> mods = modOnly.get(clsName);
				if (!Objects.equals(mods, currentLoadedMods)) {
					flush();
					if (mods != null && !mods.isEmpty()) {
						currentLoadedMods = mods;
						currentLoadedModEnd = new Label();
						boolean first = true;
						for (String mod : mods) {
							method.visitLdcInsn(mod);
							method.visitMethodInsn(Opcodes.INVOKESTATIC, "minetweaker/MineTweakerAPI", "isModLoaded", "(Ljava/lang/String;)Z", false);
							if (first) {
								first = false;
							} else {
								method.visitInsn(Opcodes.IAND);
							}
						}
						method.visitJumpInsn(Opcodes.IFEQ, currentLoadedModEnd);
					}
				}
			}

			void flush() {
				if (currentLoadedMods != null) {
					method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
					method.visitLabel(currentLoadedModEnd);
					currentLoadedMods = null;
					currentLoadedModEnd = null;
				}
			}
		}

		LoadedModsHelper lmHelper = new LoadedModsHelper();

		for (String clsName : classNames) {
			lmHelper.update(clsName);
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitLdcInsn(Type.getType("L" + clsName + ";"));
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z");
			method.visitInsn(Opcodes.POP);
		}

		for (OnRegisterMethod onRegisterMethod : onRegisterMethods) {
			lmHelper.update(onRegisterMethod.className);
			method.visitMethodInsn(Opcodes.INVOKESTATIC, onRegisterMethod.className, onRegisterMethod.methodName, "()V");
		}

		lmHelper.flush();

		method.visitInsn(Opcodes.RETURN);

		method.visitMaxs(0, 0);
		method.visitEnd();

		output.visitEnd();

		// write output file
		File outputFile = new File(outputDir, fullClassName + ".class");
		File outputFileDir = outputFile.getParentFile();
		if (!outputFileDir.exists()) {
			outputFileDir.mkdirs();
		}
		if (outputFile.exists()) {
			outputFile.delete();
		}

		try {
			FileOutputStream outputStream = new FileOutputStream(outputFile);
			outputStream.write(output.toByteArray());
			outputStream.close();
		} catch (IOException ex) {
			Logger.getLogger(RegisterZenClassesTask.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void iterate(File dir, String pkg, List<String> classNames, Map<String, List<String>> modOnly, List<OnRegisterMethod> onRegisterMethods) {
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				if (pkg == null) {
					iterate(f, f.getName(), classNames, modOnly, onRegisterMethods);
				} else {
					iterate(f, pkg + "/" + f.getName(), classNames, modOnly, onRegisterMethods);
				}
			} else if (f.isFile()) {
				if (f.getName().endsWith(".class")) {
					processJavaClass(f, pkg, classNames, modOnly, onRegisterMethods);
				}
			}
		}
	}

	private void processJavaClass(File cls, String pkg, List<String> classNames, Map<String, List<String>> modOnly, List<OnRegisterMethod> onRegisterMethods) {
		try {
			InputStream input = new BufferedInputStream(new FileInputStream(cls));
			ClassReader reader = new ClassReader(input);

			AnnotationDetector detector = new AnnotationDetector();
			reader.accept(detector, ClassReader.SKIP_CODE);
			input.close();

			class LazyClassName {
				String name = null;

				String get() {
					if (name != null)
						return name;
					name = pkg + "/" + cls.getName().substring(0, cls.getName().length() - 6);
					if (detector.modOnly != null) {
						detector.modOnly.sort(Comparator.naturalOrder());
						modOnly.put(name, detector.modOnly);
					}
					return name;
				}
			}

			LazyClassName cn = new LazyClassName();

			if (detector.isAnnotated) {
				classNames.add(cn.get());
			}
			for (MethodAnnotationDetector onRegisterMethod : detector.onRegister) {
				onRegisterMethods.add(new OnRegisterMethod(
						cn.get(),
						onRegisterMethod.name));
			}
		} catch (IOException ex) {

		}
	}

	private static class AnnotationDetector extends ClassVisitor {
		private boolean isAnnotated = false;
		private List<String> modOnly = null;
		private List<MethodAnnotationDetector> onRegister = new ArrayList<MethodAnnotationDetector>();

		public AnnotationDetector() {
			super(Opcodes.ASM4);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (desc.equals("Lstanhebben/zenscript/annotations/ZenExpansion;")) {
				isAnnotated = true;
			} else if (desc.equals("Lstanhebben/zenscript/annotations/ZenClass;")) {
				isAnnotated = true;
			} else if (desc.equals("Lminetweaker/annotations/BracketHandler;")) {
				isAnnotated = true;
			}
			if (desc.equals("Lminetweaker/annotations/ModOnly;")) {
				return new AnnotationVisitor(Opcodes.ASM4) {
					@Override
					public AnnotationVisitor visitArray(String name) {
						if ("value".equals(name)) {
							return new AnnotationVisitor(Opcodes.ASM4) {
								@Override
								public void visit(String name, Object value) {
									if (modOnly == null) {
										modOnly = new ArrayList<>();
									}
									modOnly.add((String) value);
								}
							};
						}
						return super.visitArray(name);
					}
				};
			}

			return super.visitAnnotation(desc, visible);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			return new MethodAnnotationDetector(this, name, desc);
		}
	}

	private static class MethodAnnotationDetector extends MethodVisitor {
		private final AnnotationDetector detector;
		private final String name;
		private final String desc;

		public MethodAnnotationDetector(AnnotationDetector detector, String name, String desc) {
			super(Opcodes.ASM4);

			this.detector = detector;
			this.name = name;
			this.desc = desc;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (desc.equals("Lminetweaker/annotations/OnRegister;")) {
				if (this.desc.equals("()V")) {
					detector.onRegister.add(this);
				} else {
					throw new RuntimeException("OnRegister annotation must be used on a static method without arguments or return value");
				}
			}

			return super.visitAnnotation(desc, visible);
		}
	}

	private static class OnRegisterMethod {
		private final String className;
		private final String methodName;

		public OnRegisterMethod(String className, String methodName) {
			this.className = className;
			this.methodName = methodName;
		}
	}
}
