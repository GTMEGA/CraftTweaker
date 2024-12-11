import minetweaker.tasks.RegisterZenClassesTask

plugins {
    id("fpgradle-minecraft") version("0.8.3")
}

group = "minetweaker"

minecraft_fp {
    mod {
        modid   = "MineTweaker3"   //REQUIRED
        name    = "MineTweaker 3"  //REQUIRED
        rootPkg = "minetweaker" //REQUIRED
    }
    publish {
        maven {
            repoUrl  = "https://example.com/"
            repoName = "example"
            group    = "${project.group}"      //Convention
            artifact = "minetweaker-mc1.7.10" //Convention
        }
    }
}

tasks {
    val makeRegistry = register<RegisterZenClassesTask>("makeRegistry")
    makeRegistry.configure {
        inputDir = file("build/classes/java/main/")
        outputDir = file("build/classes/java/main/")
        className = "minetweaker.mc1710.MineTweakerRegistry"
        mustRunAfter("compileJava")
    }

    classes.configure {
        dependsOn(makeRegistry)
    }
}

repositories {
    exclusive(maven("horizon", "https://mvn.falsepattern.com/horizon"), "com.github.GTNewHorizons")
    exclusive(mega(), "codechicken")
    exclusive(maven("ic2", "https://mvn.falsepattern.com/ic2/") {
        metadataSources {
            artifact()
        }
    }, "net.industrial-craft")
}

dependencies {
    shadowImplementation("com.github.GTNewHorizons:ZenScript:1.0.0-GTNH")
    compileOnly("net.industrial-craft:industrialcraft-2:2.2.828-experimental:dev")
    implementation("codechicken:codechickencore-mc1.7.10:1.4.0-mega:dev")
    compileOnly("codechicken:notenoughitems-mc1.7.10:2.3.0-mega:dev")
}