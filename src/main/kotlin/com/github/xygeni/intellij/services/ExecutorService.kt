package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.logger.Logger
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import java.io.File


class DefaultProcessExecutorService : ProcessExecutorService() {}

class MyProcessHandle {
    // Volátiles para seguridad entre hilos
    @Volatile
    private var handler: OSProcessHandler? = null

    @Volatile
    private var process: Process? = null

    // Si el usuario solicita parar antes de que exista el process/handler
    @Volatile
    private var shouldStop = false

    fun attach(process: Process, handler: OSProcessHandler) {
        this.process = process
        this.handler = handler

        // Si ya pedimos stop antes de adjuntar, lo paramos ya.
        if (shouldStop) {
            try {
                handler.destroyProcess()
            } catch (_: Exception) {
                process.destroyForcibly()
            }
        }
    }

    fun stop(project : Project) {
        Logger.log("Stopping process...", project)
        val h = handler
        val p = process

        try {
            when {
                // Stop
                h != null -> {
                    h.destroyProcess()
                }
                p != null -> {
                    // Kill the process and its descendants
                    val handle = p.toHandle()
                    try {
                        handle.descendants().forEach { it.destroyForcibly() }
                    } catch (_: Exception) { /* ignore */ }
                    if (p.isAlive) {
                        p.destroyForcibly()
                    }
                }
                else -> {
                    shouldStop = true
                }
            }
        }catch (e: Exception) {
            Logger.error("Error stopping process", e, project)
            // Ignore
        }
    }

    fun isRunning(): Boolean = process?.isAlive ?: false
}

abstract class ProcessExecutorService {

    /**
     * Executes a script  (.ps1, .sh) o a fin (.exe, ELF) asynchronous
     *
     * @param path Path to exe file
     * @param args process arguments
     * @param onComplete Callback with true when the process exits successfully, false if it failed.
     */
    fun executeProcess(
        path: String,
        args: Map<String, String>,
        envs: Map<String, String>?,
        workingDir: File? = null,
        project: Project? = null,
        onComplete: (success: Boolean) -> Unit
    ): MyProcessHandle?  {
        val processHandle = MyProcessHandle()

        ApplicationManager.getApplication().executeOnPooledThread {
            var success = false

            try {

                // Check envs:
                var emptyEnv = false
                envs?.forEach { (key, value) ->
                    if (value.isNullOrBlank()  ) {
                        Logger.error("❌ $key cannot be empty", project)
                        emptyEnv = true
                    }
                }
                if (emptyEnv) {
                    ApplicationManager.getApplication().invokeLater { onComplete(false) }
                    return@executeOnPooledThread
                }

                val file = File(path)
                if (!file.exists()) {
                    Logger.error("❌ The file $path does not exist", project)
                    ApplicationManager.getApplication().invokeLater { onComplete(false) }
                    return@executeOnPooledThread
                }

                val command = buildCommand(path, args)
                val commandLine = GeneralCommandLine(command)
                    .withWorkDirectory(workingDir)
                    .withEnvironment(envs ?: emptyMap())
                    .withRedirectErrorStream(false)

                // Manage PATH and JAVA_HOME
                val parentEnv = commandLine.parentEnvironment
                System.getenv("JAVA_HOME")?.let { commandLine.withEnvironment("JAVA_HOME", it) }
                
                System.getenv("PATH")?.let { systemPath ->
                    val currentPath = commandLine.environment["PATH"] ?: ""
                    val updatedPath = if (currentPath.isEmpty()) systemPath else "$systemPath${File.pathSeparator}$currentPath"
                    commandLine.withEnvironment("PATH", updatedPath)
                }

                Logger.log("Command environment: JAVA_HOME=${commandLine.environment["JAVA_HOME"]}, PATH=${commandLine.environment["PATH"]}", project)

                val handler = object : OSProcessHandler(commandLine) {
                    override fun readerOptions(): BaseOutputReader.Options {
                        return BaseOutputReader.Options.forMostlySilentProcess()
                    }
                }

                val process = handler.process
                processHandle.attach(process, handler)

                handler.addProcessListener(object : ProcessListener {

                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        val text = event.text.trimEnd()
                        if (text.isNotBlank()) {
                            when (outputType) {
                                ProcessOutputTypes.STDOUT -> Logger.log(text, project)
                                ProcessOutputTypes.STDERR -> Logger.error(text, project)
                                else -> Logger.log(text, project)
                            }
                        }
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        val exitCode = event.exitCode
                        success = exitCode == 0

                        if (success) {
                            Logger.log("✅ Process finished successfully", project)
                        } else {
                            Logger.error("❌ Process finished with errors (exitCode=$exitCode)", project)
                        }

                        // Notificamos en el hilo de la UI
                        ApplicationManager.getApplication().invokeLater {
                            onComplete(success)
                        }
                    }
                })

                handler.startNotify()

            } catch (e: Exception) {
                Logger.error("💥 Error Executing the process", e, project)
                ApplicationManager.getApplication().invokeLater { onComplete(false) }
            }
        }
        return processHandle
    }

    private fun buildCommand(path: String, args: Map<String, String>): MutableList<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val file = File(path)
        val extension = file.extension.lowercase()

        val baseCommand = when {
            extension == "ps1" && isWindows -> mutableListOf(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                path
            )

            extension == "sh" && !isWindows -> mutableListOf("bash", path)
            else -> mutableListOf(path) // exe
        }

        args.forEach { (flag, value) ->
            baseCommand.add(flag)
            if (value.isNotBlank()) baseCommand.add(value)
        }

        return baseCommand
    }
}
