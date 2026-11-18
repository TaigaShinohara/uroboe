// Repository resolution:
// - Normal dev machines: google() / mavenCentral() / gradlePluginPortal().
// - This project's original build VM: Java TLS through the egress proxy gets
//   connection-reset, so dependencies are resolved through a local curl-backed
//   mirror. That route is used only when the UROBOE_MIRROR environment variable
//   is set (build.sh sets it); it never affects a normal clone.
// NOTE: in a Kotlin settings script, a val declared outside pluginManagement {}
// is not visible inside it, so the lookup is repeated inside each block.
pluginManagement {
    val mirror = System.getenv("UROBOE_MIRROR")
    repositories {
        if (mirror != null) {
            maven { url = uri("$mirror/google"); isAllowInsecureProtocol = true }
            maven { url = uri("$mirror/central"); isAllowInsecureProtocol = true }
            maven { url = uri("$mirror/plugins"); isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
dependencyResolutionManagement {
    val mirror = System.getenv("UROBOE_MIRROR")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (mirror != null) {
            maven { url = uri("$mirror/google"); isAllowInsecureProtocol = true }
            maven { url = uri("$mirror/central"); isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
        }
    }
}
rootProject.name = "Uroboe"
include(":app")
