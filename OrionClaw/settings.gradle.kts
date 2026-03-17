pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()

    // AgentOS SDK Maven repo (v0.4.5 doc)
    maven {
      credentials.username = "agentMaven"
      credentials.password = "agentMaven"
      url = uri("https://npm.ainirobot.com/repository/maven-public/")
    }

    // keep for other deps
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "OrionClaw"
include(":app")
