@file:Suppress("UnstableApiUsage")

package com.github.esafak.nimintellijplugin

import com.github.esafak.nimintellijplugin.settings.NimSettingsState
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.notification.NotificationType
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.FindReferencesSupport
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCommandsSupport
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.util.applyIf
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.PrepareSupportDefaultBehavior
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.Icon

class NimLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Nim") {
    
    private val lspLogger = logger<NimLspServerDescriptor>()

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "nim"

    override fun createCommandLine(): GeneralCommandLine {
        val path = NimSettingsState.instance.nimlangserverPath
        if (path.isEmpty() || !File(path).exists()) {
            Notifications.Bus.notify(
                Notification(
                    "NimLangServer",
                    "Nim langserver not found",
                    "Please configure the path to the nimlangserver executable in the settings.",
                    NotificationType.ERROR
                )
            )
            return GeneralCommandLine()
        }

        val pathFromShell = getPathFromShell()
        if (!pathFromShell.hasCommands("nimble", "nimsuggest")) {
            issueError("`nimble` or `nimsuggest` not found in PATH. Cannot start Nim langserver.")
        }

        // Add client process ID for monitoring
        val pid = ProcessHandle.current().pid()

        val commandLine = GeneralCommandLine(path)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment("PATH", pathFromShell!!)
            .withParameters("--stdio", "--clientProcessId=$pid")
            .withRedirectErrorStream(true)  // Redirect stderr to stdout

        return commandLine
    }

    private fun String?.hasCommands(vararg commands: String): Boolean {
        if (isNullOrEmpty()) return false
        val unresolvedCommands = commands.toMutableSet()
        split(File.pathSeparator).forEach { dir ->
            val iter = unresolvedCommands.iterator()
            while (iter.hasNext()) {
                val command = iter.next()
                if (File(dir, command).canExecute())
                    iter.remove()
            }
            if (unresolvedCommands.isEmpty()) return true
        }
        return false
    }

    private fun getPathFromShell(): String? {
        val osName = System.getProperty("os.name")
        val shell: String
        val args: List<String>

        // TODO: Test the Windows case
        if (osName.startsWith("Windows")) {
            shell = "cmd.exe"
            args = listOf("/c", "echo %PATH%")
        } else {
            shell = "sh"
            args = listOf("-cl", "echo \$PATH")
        }

        return try {
            return ProcessBuilder(shell, *args.toTypedArray()).start().also {
                it.waitFor(1, TimeUnit.SECONDS)
            }.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            issueError("Failed to get PATH from shell: ${e.message}")
            null
        }
    }

    private fun issueError(message: String) {
        Notifications.Bus.notify(
            Notification(
                "NimLangServer",
                "PATH issue",
                message,
                NotificationType.ERROR
            )
        )
    }

    override fun createInitializeParams(): InitializeParams {
        val params = super.createInitializeParams()

        // Configure workspace capabilities
        params.capabilities.workspace.apply {
            configuration = true
            didChangeConfiguration?.dynamicRegistration = true
            symbol?.dynamicRegistration = true
            executeCommand?.dynamicRegistration = true

            // Enable inlay hint refresh support if available
            inlayHint?.refreshSupport = true
        }

        // Configure text document capabilities
        params.capabilities.textDocument.apply {
            // Enable hover support
            hover?.contentFormat = listOf("markdown", "plaintext")

            // Enable completion support
            completion?.apply {
                completionItem?.apply {
                    snippetSupport = true
                    documentationFormat = listOf("markdown", "plaintext")
                    commitCharactersSupport = true
                    deprecatedSupport = true
                    preselectSupport = true
                }
                contextSupport = true
            }

            // Enable signature help
            signatureHelp?.apply {
                signatureInformation?.apply {
                    documentationFormat = listOf("markdown", "plaintext")
                    parameterInformation?.labelOffsetSupport = true
                    activeParameterSupport = true
                }
                contextSupport = true
            }

            // Enable document symbols
            documentSymbol?.hierarchicalDocumentSymbolSupport = true

            // Enable references
            references?.dynamicRegistration = true

            // Enable definition
            definition?.dynamicRegistration = true
            definition?.linkSupport = true

            // Enable declaration
            declaration?.dynamicRegistration = true
            declaration?.linkSupport = true

            // Enable type definition
            typeDefinition?.dynamicRegistration = true
            typeDefinition?.linkSupport = true

            // Enable document highlight
            documentHighlight?.dynamicRegistration = true

            // Enable inlay hints
            inlayHint?.dynamicRegistration = true
            inlayHint?.resolveSupport?.properties = listOf("tooltip", "textEdits", "label.tooltip", "label.location", "label.command")

            // Enable semantic tokens
            semanticTokens?.apply {
                tokenTypes = listOf(
                    "namespace", "type", "class", "enum", "interface",
                    "struct", "typeParameter", "parameter", "variable", "property",
                    "enumMember", "event", "function", "method", "macro",
                    "keyword", "modifier", "comment", "string", "number",
                    "regexp", "operator"
                )
                tokenModifiers = listOf(
                    "declaration", "definition", "readonly", "static",
                    "deprecated", "abstract", "async", "modification", "documentation",
                    "defaultLibrary"
                )
            }

            // Enable code actions
            codeAction?.codeActionLiteralSupport?.codeActionKind?.valueSet = listOf(
                "quickfix", "refactor", "refactor.extract", "refactor.inline",
                "refactor.rewrite", "source", "source.organizeImports"
            )

            // Enable "rename with prepare" support
            rename?.apply {
                prepareSupport = true
                prepareSupportDefaultBehavior = PrepareSupportDefaultBehavior.forValue(1)
                honorsChangeAnnotations = true
            }

            // Enable formatting
            formatting?.dynamicRegistration = true
        }

        lspLogger.debug("Initialized params: $params")
        return params
    }

    // Configure signature help options in server initialization
    override fun createInitializationOptions(): Any? {
        // Create signature help options
        val signatureHelpOptions = SignatureHelpOptions()
        signatureHelpOptions.triggerCharacters = listOf("(", ",", "[")
        signatureHelpOptions.retriggerCharacters = listOf(",", ")")

        // Provide Nim-specific initialization options
        return mapOf(
            "provideFormatter" to true,
            "nimblePath" to NimSettingsState.instance.nimblePath,
            "nimPath" to NimSettingsState.instance.nimPath,
            "inlayHints" to mapOf(
                "enable" to true,
                "typeHints" to true,
                "parameterHints" to true,
                "exceptionHints" to true
            ),
            "signatureHelp" to mapOf(
                "enable" to true,
                "options" to signatureHelpOptions
            )
        )
    }

    // Enable completion support with custom behavior if needed
    override val lspCompletionSupport: LspCompletionSupport? = object : LspCompletionSupport() {
//        override fun getCompletionOptions() = CompletionOptions(true, listOf(".", ":", "^"))

        override fun createLookupElement(parameters: CompletionParameters, item: CompletionItem): LookupElement? =
            LookupElementBuilder.create(item.label)
                .withIcon(getIcon(item))
                .withTypeText(getTypeText(item))
                .withTailText(getTailText(item))
                .applyIf(isBold(item)) { this.bold() }
                .applyIf(isStrikeout(item)) { this.strikeout() }

        override fun getIcon(item: CompletionItem): Icon? {
            return when (item.kind) {
                CompletionItemKind.Function -> when {
                    item.detail?.startsWith("iterator") == true -> NimIcons.ITERATOR
                    item.detail?.startsWith("converter") == true -> NimIcons.CONVERTER
                    else -> NimIcons.PROCEDURE
                }
                CompletionItemKind.Method -> NimIcons.METHOD
                CompletionItemKind.Class -> NimIcons.TYPE
                CompletionItemKind.Variable -> when {
                    item.detail?.startsWith("param") == true -> NimIcons.PARAMETER
                    else -> NimIcons.VARIABLE
                }
                CompletionItemKind.Constant -> NimIcons.CONSTANT
                CompletionItemKind.Field -> NimIcons.FIELD
                CompletionItemKind.Snippet -> NimIcons.MACRO
                CompletionItemKind.Enum -> NimIcons.ENUM
                CompletionItemKind.EnumMember -> NimIcons.ENUM_MEMBER
                CompletionItemKind.Keyword -> NimIcons.TEMPLATE
                CompletionItemKind.Module -> NimIcons.MODULE
                else -> null
            }
        }

        override fun getTailText(item: CompletionItem): String? {
            // For procedures/methods, show the full signature
            // For variables/fields, show the type
            return when (item.kind) {
                CompletionItemKind.Function,
                CompletionItemKind.Method -> item.detail
                CompletionItemKind.Variable,
                CompletionItemKind.Field,
                CompletionItemKind.Property -> item.detail?.substringAfter(":")?.trim()
                else -> null
            }
        }

        override fun getTypeText(item: CompletionItem): String? {
            // For procedures/methods, show the return type
            // For variables/fields, show "var", "let", or "const"
            return when (item.kind) {
                CompletionItemKind.Function,
                CompletionItemKind.Method -> item.detail?.substringAfterLast("->")?.trim()
                CompletionItemKind.Variable -> "var"
                CompletionItemKind.Constant -> "const"
                CompletionItemKind.Field -> "field"
                else -> null
            }
        }

        override fun isBold(item: CompletionItem): Boolean {
            return item.kind == CompletionItemKind.Snippet || // Macros and templates
                   item.kind == CompletionItemKind.Keyword ||
                   item.kind == CompletionItemKind.Function || // Public procedures
                   item.kind == CompletionItemKind.Method     // Public methods
        }

        override fun isStrikeout(item: CompletionItem): Boolean =
            item.tags?.contains(CompletionItemTag.Deprecated) == true

        override fun renderLookupElement(item: CompletionItem, presentation: LookupElementPresentation) {
            presentation.icon = getIcon(item)
            presentation.itemText = item.label
            presentation.typeText = getTypeText(item)
            presentation.tailText = getTailText(item)
            presentation.isStrikeout = isStrikeout(item)
            presentation.isItemTextBold = isBold(item)
        }

        override fun shouldRunCodeCompletion(parameters: CompletionParameters): Boolean {
            val char = parameters.position.text.getOrNull(parameters.offset - 1)
            return char in listOf('.', ':', '^')
        }
    }

    override val lspSemanticTokensSupport: LspSemanticTokensSupport? = object : LspSemanticTokensSupport() {
        override val tokenTypes: List<String> = listOf(
            "variable", "constant", "property", "enumMember", "function", "method",
            "class", "interface", "type", "parameter", "macro", "keyword",
            "modifier", "comment", "string", "number", "operator", "namespace",
            "typeParameter"
        )

        override val tokenModifiers: List<String> = listOf(
            "declaration", "definition", "readonly", "static", "deprecated", "documentation"
        )

        // TODO Provide mapping from token types and modifiers to TextAttributesKey for editor highlighting
//        override fun getTextAttributesKey(
//            tokenType: String,
//            modifiers: List<String>
//        ): com.intellij.openapi.editor.colors.TextAttributesKey? {
//            return null
//        }
    }

    // Enable find references support
    override val lspFindReferencesSupport: FindReferencesSupport? = object : FindReferencesSupport() {}

    // Enable formatting support
    override val lspFormattingSupport: LspFormattingSupport? = object : LspFormattingSupport() {
        override fun shouldFormatThisFileExclusivelyByServer(
            file: VirtualFile,
            ideCanFormatThisFileItself: Boolean,
            serverExplicitlyWantsToFormatThisFile: Boolean
        ): Boolean = file.extension == "nim" // Let LSP handle all .nim files
    }

    // Enable code actions support
    override val lspCodeActionsSupport: LspCodeActionsSupport? = object : LspCodeActionsSupport() {
        override val quickFixesSupport: Boolean = true
        override val intentionActionsSupport: Boolean = true
        override fun createIntentionAction(lspServer: LspServer, codeAction: CodeAction): LspIntentionAction {
            return LspIntentionAction(lspServer, codeAction)
        }
        override fun createQuickFix(lspServer: LspServer, codeAction: CodeAction): LspIntentionAction {
            return LspIntentionAction(lspServer, codeAction)
        }
    }

    // Enable command support for Nim-specific commands
    override val lspCommandsSupport: LspCommandsSupport? = object : LspCommandsSupport() {
        @RequiresEdt
        override fun executeCommand(server: LspServer, contextFile: VirtualFile, command: Command) {
            when (command.command) {
                "nimlangserver.restart",
                "nimlangserver.recompile",
                "nimlangserver.checkProject" -> {
                    super.executeCommand(server, contextFile, command)
                }
            }
        }
    }

    // Server lifecycle listener for proper shutdown and exit handling
    override val lspServerListener: LspServerListener? = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
            super.serverInitialized(params)
            lspLogger.info("Server info: ${params.serverInfo}")
            lspLogger.info("Server capabilities: ${params.capabilities}")
        }
    }

    // Handle process termination properly
    override fun startServerProcess(): OSProcessHandler {
        val processHandler = super.startServerProcess()

        // Add a termination handler to ensure proper cleanup
        processHandler.addProcessListener(object : com.intellij.execution.process.ProcessAdapter() {
            override fun processTerminated(event: com.intellij.execution.process.ProcessEvent) {
                lspLogger.info("Nim language server process terminated with exit code: ${event.exitCode}")

                // If the process terminated abnormally, show a notification
                if (event.exitCode != 0) {
                    Notifications.Bus.notify(
                        Notification(
                            "NimLangServer",
                            "Nim language server terminated unexpectedly",
                            "The Nim language server process terminated with exit code ${event.exitCode}. " +
                                    "You may need to restart the IDE or check the server configuration.",
                            NotificationType.WARNING
                        )
                    )
                }
            }
        })

        return processHandler
    }

    // Diagnostics support is handled by the base class
    override val lspDiagnosticsSupport: LspDiagnosticsSupport? = null

    // These features are enabled through the initialization parameters
    override val lspGoToDefinitionSupport: Boolean = true

    override val lspHoverSupport: Boolean = true

    override val lsp4jServerClass: Class<out LanguageServer> = LanguageServer::class.java

//    override fun getWorkspaceConfiguration(item: org.eclipse.lsp4j.ConfigurationItem): Any? {
//        // Return Nim-specific configuration
//        return mapOf(
//            "nim" to mapOf(
//                "checkOnSave" to true,
//                "autoCheckProject" to true,
//                "useNimCheck" to true,
//                "logNimsuggest" to false,
//                "nimsuggestIdleTimeout" to 120000,
//                "maxNimsuggestProcesses" to 1,
//                "inlayHints" to mapOf(
//                    "typeHints" to mapOf("enable" to true),
//                    "parameterHints" to mapOf("enable" to true),
//                    "exceptionHints" to mapOf(
//                        "enable" to true,
//                        "hintStringLeft" to "⚠️ ",
//                        "hintStringRight" to ""
//                    )
//                ),
//                "formatOnSave" to true
//            )
//        )
//    }
}