pluginManagement {
    repositories {
        // El Maven de Google se consulta SOLO para sus propios grupos. Sin este
        // filtro, Gradle lo probaria para todas las dependencias y cada
        // resolucion pagaria una peticion inutil.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CacaoTrace"

// :nucleo es Kotlin puro, sin nada de Android. Esa separacion no es estetica:
// permite ejecutar sus tests en la JVM en segundos, reutilizar las reglas en
// otra plataforma, y garantiza que la logica de negocio no pueda depender por
// accidente de un Context o de una vista.
include(":nucleo")
include(":app")
