import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    java
    alias(libs.plugins.pluginYml)
}

group = "uk.co.notnull"
version = "1.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
    mavenLocal()
}

dependencies {
	compileOnly(libs.paperApi)
	compileOnly(libs.placeholderApi)
    compileOnly(libs.worldguard)
}

paper {
    main = "uk.co.notnull.pvp.PvP"
    apiVersion = libs.versions.paperApi.get().replace(".build.+", "")
    authors = listOf("Jim (AnEnragedPigeon)")
    description = "Toggleable PvP protections"

    permissions {
        register("pvp.toggle") {
            description = "Allows toggling of your own PvP state"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("pvp.toggle.other") {
            description = "Allows toggling of other players PvP states"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("pvp.info") {
            description = "Allows viewing your own PvP state"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("pvp.info.other") {
            description = "Allows viewing of other players PvP states"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("pvp.reload") {
            description = "Allows reloading the plugin"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }

    serverDependencies {
        register("PlaceholderAPI") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("WorldGuard") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.AFTER
        }
    }
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }
}
