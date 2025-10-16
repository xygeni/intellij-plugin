# Introducción a Kotlin, Gradle y los plugins de Intellij
El objetivo de este documento es presentar un resumen de lo aprendido a la hora de implementar el plugin de IntelliJ

## Indice
* [Arquitectura general del proyecto](#1-arquitectura-general-del-proyecto)
* [`build.gradle.kts` — el cerebro del proyecto](#2-buildgradlekts--el-cerebro-del-proyecto)
* [`libs.versions.toml` — el catálogo de versiones](#3-libsversionstoml--el-catálogo-de-versiones)
* [`settings.gradle.kts` — el inicio del proyecto](#4-settingsgradlekts--el-inicio-del-proyecto)
* [`plugin.xml` — el manifesto de tu plugin](#5-pluginxml--el-manifesto-de-tu-plugin)
* [Tests del plugin](#6-tests-del-plugin)
* [Limpieza y sandbox](#7-limpieza-y-sandbox)
* [Comandos clave de Gradle para plugins IntelliJ](#8-comandos-clave-de-gradle-para-plugins-intellij)
* [Tips importantes](#9-tips-importantes)
* [Resumen](#10-resumen)


## 1. Arquitectura general del proyecto

Un plugin de IntelliJ es básicamente un proyecto Gradle que:
* compila tu código Kotlin o Java,
* empaqueta un .zip o .jar con tu plugin,
* lo prueba dentro de una versión de IntelliJ (sandbox),
* y lo puede publicar en el Marketplace.

La estructura típica:
```bash
my-intellij-plugin/
├── build.gradle.kts        # Configuración principal del proyecto
├── settings.gradle.kts     # Nombre del proyecto + link a libs.versions.toml
├── gradle.properties        # Propiedades globales (opcional)
├── gradle/
│   └── libs.versions.toml  # Donde defines versiones y alias de plugins/deps
├── src/
│   ├── main/
│   │   ├── kotlin/         # Código del plugin
│   │   ├── resources/      # plugin.xml y recursos
│   └── test/
│       ├── kotlin/         # Tests (unitarios y de integración)
│       ├── resources/
└── README.md

```

## 2. `build.gradle.kts` — el cerebro del proyecto
Este archivo dice:
* Qué plugins de Gradle se usan,
* Qué versión de IntelliJ vas a probar,
* Qué versión de Kotlin,
* Qué dependencias y configuración del compilador.

```kotlin
plugins {
    alias(libs.plugins.kotlin)             // Plugin de Kotlin
    alias(libs.plugins.intelliJPlatform)   // Plugin Gradle para IntelliJ
    alias(libs.plugins.changelog)          // Genera el CHANGELOG automáticamente
    alias(libs.plugins.kover)              // Cobertura de tests
}

intellijPlatform {
    version.set("2024.3.6")   // Versión del IDE con el que se probará tu plugin
    type.set("IC")            // "IC" = IntelliJ Community, "IU" = Ultimate
    plugins.set(listOf("java")) // Plugins base que necesitas (ej: Java, Kotlin, etc.)
}

kotlin {
    jvmToolchain(21)          // Compilar y ejecutar con JDK 21 (el mismo que usa el IDE)
}

tasks {
    patchPluginXml {
        version.set(project.version.toString())  // Inserta la versión del plugin en plugin.xml
    }

    test {
        useJUnitPlatform()     // Usa JUnit 5
    }
}
```
Este archivo **describe cómo construir tu plugin y correr los tests**.
Cuando ejecutas:
```bash
./gradlew build
```
Gradle lee este archivo y decide:
* qué dependencias bajar,
* cómo compilar el código,
* y cómo crear el sandbox para probarlo.

## 3. `libs.versions.toml` — el catálogo de versiones
Este archivo es _nuevo_ en Gradle y muy limpio
Permite centralizar versiones y nombres de plugins para que no se repitan en cada build.
Ejemplo:
```toml
[versions]
kotlin = "2.0.21"
intelliJPlatform = "1.17.0"
changelog = "2.2.0"
kover = "0.9.1"

[plugins]
kotlin = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
intelliJPlatform = { id = "org.jetbrains.intellij.platform", version.ref = "intelliJPlatform" }
changelog = { id = "org.jetbrains.changelog", version.ref = "changelog" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```
Luego, en el `build.gradle.kts`, se puede escribir simplemente:

```kotlin
alias(libs.plugins.kotlin)
alias(libs.plugins.intelliJPlatform)
```

## 4. `settings.gradle.kts` — el inicio del proyecto

Este archivo le dice a Gradle:
* cómo se llama el proyecto,
* y dónde está el catálogo `libs.versions.toml`.

Ejemplo:
```kotlin
rootProject.name = "my-intellij-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
```
## 5. `plugin.xml` — el manifesto de tu plugin

Está dentro de `src/main/resources/META-INF/plugin.xml`.

Aquí se define:
* el ID del plugin,
* su nombre y versión,
* qué dependencias de plataforma usa,
* y los componentes o extensiones que registra (acciones, menús, listeners…).

Ejemplo mínimo:
```xml
<idea-plugin>
    <id>com.example.myplugin</id>
    <name>My Plugin</name>
    <version>1.0.0</version>
    <vendor email="you@example.com">Tu Nombre</vendor>

    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="com.example.MyService" />
    </extensions>

    <actions>
        <action id="MyPluginAction"
                class="com.example.MyAction"
                text="Say Hello"
                description="Muestra un mensaje">
            <add-to-group group-id="ToolsMenu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

IntelliJ usa este archivo para cargar tu plugin dentro del IDE de prueba.

## 6. Tests del plugin
En src/test/kotlin se puede usar:
* **JUnit5** para tests unitarios,
* o **IntelliJ Platform Test Framework** para tests de integración.

Ejemplo básico con `BasePlatformTestCase`:
```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyPluginTest : BasePlatformTestCase() {
    fun testPluginLoads() {
        val project = project
        assertNotNull(project)
    }
}
```
Este test levanta un `mini-IDE sandbox` con tu plugin cargado y verifica que funcione correctamente.

## 7. Limpieza y sandbox
Cuando se ejecutan los tests o el comnado `runIde`, el plugin crea un sandbox temporal aquí:
```bash
build/idea-sandbox/IC-2024.3.6/
```
Contiene carpetas como:
* config-test → configuración del IDE de test
* system-test → logs, cachés
* plugins-test → el plugin empaquetado

Si algo va mal, se puede borrar todo ese sandbox con:
```bash
./gradlew clean
```

## 8. Comandos clave de Gradle para plugins IntelliJ

| Comando                  | Qué hace                                                  |
|--------------------------|-----------------------------------------------------------|
| `./gradlew build`        | Compila, ejecuta tests y empaqueta el plugin              |
| `./gradlew test`         | 	Ejecuta los tests                                        |
| `./gradlew runIde`       | 	Lanza una instancia de IntelliJ con tu plugin instalado  |
| `./gradlew verifyPlugin` | 	Verifica que el plugin es publicable                     |
| `./gradlew publishPlugin | `	Lo sube al Marketplace (si tienes configurado el token) |

## 9. Tips importantes
* Mantener alineadas las versiones de Kotlin y del IDE (tabla oficial de compatibilidad)
* Usar `libs.versions.toml` para centralizar versiones.
* **No mezclar APIs de UI y background threads** sin usar `invokeLater`.
* **Probar siempre** con `./gradlew runIde` antes de publicar.
* **Evitar versiones EAP de IntelliJ** salvo que se necesiten features nuevas. 

## 10. Resumen
| Fichero               | Rol                                               |
|-----------------------|---------------------------------------------------|
| `build.gradle.kts`    | 	Configura cómo se construye y prueba el plugin   |
| `libs.versions.toml`  | 	Centraliza versiones de dependencias y plugins   |
| `settings.gradle.kts` | 	Conecta el proyecto y los catálogos de versiones |
| `plugin.xml`          | 	Define la identidad y las extensiones del plugin |
| `src/main/kotlin`     | 	Código fuente del plugin                         |
| `src/test/kotlin`     | 	Tests de integración/unitarios                   |
| `build/idea-sandbox`  | 	Entorno de pruebas aislado                       |