package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.logger.Logger
import com.intellij.execution.process.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import java.io.File


class DefaultProcessExecutorService : ProcessExecutorService() {}

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
    ) {
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

                val processBuilder = ProcessBuilder(command)
                    .redirectErrorStream(false)

                if (workingDir != null) {
                    processBuilder.directory(workingDir)
                }
                val env = processBuilder.environment()

                if (envs != null){
                    env.putAll(envs)
                }

                val process = processBuilder.start()

                //val handler = OSProcessHandler(process, command.joinToString(" "))
                val handler = object : OSProcessHandler(process, command.joinToString(" ")) {
                    override fun readerOptions(): BaseOutputReader.Options {
                        return BaseOutputReader.Options.forMostlySilentProcess()
                    }
                }
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
    }

    private fun buildCommand(path: String, args: Map<String, String>): MutableList<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val file = File(path)
        val extension = file.extension.lowercase()

        val baseCommand = when {
            extension == "ps1" && isWindows -> mutableListOf(
                "powershell.exe",
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
