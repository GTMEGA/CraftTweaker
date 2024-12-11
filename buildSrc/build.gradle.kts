import javax.tools.ToolProvider

plugins {
	java
}


repositories {
	mavenCentral();
}

dependencies {
	implementation(gradleApi())
	implementation("org.ow2.asm:asm-debug-all:5.0.3")
}
