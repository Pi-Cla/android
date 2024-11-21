package chat.revolt.components.markdown.jbm

import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import chat.revolt.R
import chat.revolt.activities.InviteActivity
import chat.revolt.api.REVOLT_FILES
import chat.revolt.api.RevoltAPI
import chat.revolt.api.routes.custom.fetchEmoji
import chat.revolt.api.schemas.isInviteUri
import chat.revolt.api.settings.LoadedSettings
import chat.revolt.callbacks.Action
import chat.revolt.callbacks.ActionChannel
import chat.revolt.components.generic.RemoteImage
import chat.revolt.components.markdown.Annotations
import chat.revolt.components.utils.detectTapGesturesConditionalConsume
import chat.revolt.ui.theme.FragmentMono
import chat.revolt.ui.theme.isThemeDark
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.CodeHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.launch
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

enum class JBMAnnotations(val tag: String, val clickable: Boolean) {
    URL("URL", true),
    UserMention("UserMention", true),
    ChannelMention("ChannelMention", true),
    CustomEmote("CustomEmote", true),
    Timestamp("Timestamp", false),
    Checkbox("Checkbox", false)
}

object JBMRegularExpressions {
    val Mention = Regex("<@([0-9A-Z]{26})>")
    val Channel = Regex("<#([0-9A-Z]{26})>")
    val CustomEmote = Regex(":([0-9A-Z]{26}):")
    val Timestamp = Regex("<t:([0-9]+?)(:[tTDfFR])?>")
}

data class JBMColors(
    val clickable: Color,
    val clickableBackground: Color,
)

data class JBMarkdownTreeState(
    val sourceText: String = "",
    val listDepth: Int = 0,
    val fontSizeMultiplier: Float = 1f,
    val linksClickable: Boolean = true,
    val currentServer: String? = null,
    val embedded: Boolean = false,
    val singleLine: Boolean = false,
    val colors: JBMColors = JBMColors(
        clickable = Color(0xFFFF00FF),
        clickableBackground = Color(0x2000FF00)
    )
)

val LocalJBMarkdownTreeState =
    compositionLocalOf(structuralEqualityPolicy()) { JBMarkdownTreeState() }

@Composable
@JBM
fun JBMRenderer(content: String, modifier: Modifier = Modifier) {
    var tree by remember { mutableStateOf(JBMApi.parse(content)) }

    LaunchedEffect(content) {
        tree = JBMApi.parse(content)
    }

    CompositionLocalProvider(
        LocalJBMarkdownTreeState provides LocalJBMarkdownTreeState.current.copy(
            sourceText = content,
            colors = JBMColors(
                clickable = MaterialTheme.colorScheme.primary,
                clickableBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )
    ) {
        if (LocalJBMarkdownTreeState.current.embedded) {
            tree.children.getOrNull(0)?.let {
                JBMBlock(it, modifier)
            }
        } else {
            tree.children.map {
                JBMBlock(it, modifier)
            }
        }
    }
}

private fun annotateText(
    state: JBMarkdownTreeState,
    node: ASTNode
): AnnotatedString {
    val sourceText = state.sourceText

    return try {
        buildAnnotatedString {
            when (node.type) {
                MarkdownTokenTypes.TEXT -> {
                    val source = if (state.embedded) {
                        node.getTextInNode(sourceText).toString().replace("\n", " ")
                    } else {
                        node.getTextInNode(sourceText)
                    }
                    append(source)
                }

                MarkdownTokenTypes.ATX_HEADER -> {
                    // Do not need to do anything
                }

                MarkdownElementTypes.ATX_1,
                MarkdownElementTypes.ATX_2,
                MarkdownElementTypes.ATX_3,
                MarkdownElementTypes.ATX_4,
                MarkdownElementTypes.ATX_5,
                MarkdownElementTypes.ATX_6 -> {
                    for (child in node.children) {
                        append(annotateText(state, child))
                    }
                }

                MarkdownElementTypes.EMPH -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        // Skip the first child and the last child
                        // because they are the asterisk characters
                        for (child in node.children.subList(1, node.children.size - 1)) {
                            append(annotateText(state, child))
                        }
                    }
                }

                MarkdownElementTypes.STRONG -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        // Skip the first two children and the last two children
                        // because they are the asterisk characters
                        for (child in node.children.subList(2, node.children.size - 2)) {
                            append(annotateText(state, child))
                        }
                    }
                }

                GFMElementTypes.STRIKETHROUGH -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        // Skip the first two children and the last two children
                        // because they are the tilde characters
                        for (child in node.children.subList(2, node.children.size - 2)) {
                            append(annotateText(state, child))
                        }
                    }
                }

                MarkdownElementTypes.CODE_SPAN -> {
                    withStyle(SpanStyle(fontFamily = FragmentMono)) {
                        val startsWithTwoBackticks =
                            node.children.getOrNull(1)?.type == MarkdownTokenTypes.BACKTICK
                        val removeItemCount = if (startsWithTwoBackticks) 2 else 1
                        // Skip the first and last 1 or 2 children
                        // because they are the backtick characters
                        for (child in node.children.subList(
                            removeItemCount,
                            node.children.size - removeItemCount
                        )) {
                            append(annotateText(state, child))
                        }
                    }
                }

                MarkdownTokenTypes.LIST_BULLET -> {
                    append(" ".repeat(state.listDepth) + " • ")
                }

                MarkdownTokenTypes.LIST_NUMBER -> {
                    withStyle(SpanStyle(fontFeatureSettings = "'tnum'")) {
                        append(" ".repeat(state.listDepth) + "${node.getTextInNode(sourceText)} ")
                    }
                }

                MarkdownElementTypes.UNORDERED_LIST,
                MarkdownElementTypes.ORDERED_LIST,
                MarkdownElementTypes.LIST_ITEM -> {
                    for (child in node.children) {
                        append(annotateText(state, child))
                    }
                }

                GFMTokenTypes.CHECK_BOX -> {
                    if (node.getTextInNode(sourceText).trim() == "[ ]") {
                        appendInlineContent(JBMAnnotations.Checkbox.tag, "❌")
                    } else {
                        appendInlineContent(JBMAnnotations.Checkbox.tag, "✅")
                    }
                    append(" ")
                }

                MarkdownElementTypes.PARAGRAPH,
                MarkdownElementTypes.HTML_BLOCK -> {
                    for (child in node.children) {
                        append(annotateText(state, child))
                    }
                }

                MarkdownTokenTypes.ATX_CONTENT -> {
                    // Drop WHITE_SPACE children at the start
                    for (child in node.children.dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }) {
                        append(annotateText(state, child))
                    }
                }

                MarkdownElementTypes.INLINE_LINK -> {
                    val linkTextChild =
                        node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    val linkDestinationChild =
                        node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                            ?: node.children.firstOrNull { it.type == MarkdownElementTypes.AUTOLINK }

                    pushStringAnnotation(
                        tag = JBMAnnotations.URL.tag,
                        annotation = linkDestinationChild?.getTextInNode(sourceText).toString()
                            .removeSurrounding("<", ">")
                    )
                    pushStyle(SpanStyle(color = state.colors.clickable))
                    linkTextChild?.children
                        ?.drop(1) // l-bracket
                        ?.dropLast(1) // r-bracket
                        ?.forEach {
                            append(annotateText(state, it))
                        }
                    pop()
                    pop()
                }

                GFMTokenTypes.GFM_AUTOLINK,
                MarkdownTokenTypes.AUTOLINK -> {
                    pushStringAnnotation(
                        tag = JBMAnnotations.URL.tag,
                        annotation = node.getTextInNode(sourceText).toString()
                            .removeSurrounding("<", ">")
                    )
                    pushStyle(SpanStyle(color = state.colors.clickable))
                    append(node.getTextInNode(sourceText))
                    pop()
                    pop()
                }

                // re-render types
                // for example, various syntactic elements like exclamation marks, brackets, etc.
                // we simply append the text as is
                MarkdownTokenTypes.EXCLAMATION_MARK,
                MarkdownTokenTypes.LBRACKET,
                MarkdownTokenTypes.RBRACKET,
                MarkdownTokenTypes.LPAREN,
                MarkdownTokenTypes.RPAREN,
                MarkdownTokenTypes.LT,
                MarkdownTokenTypes.GT,
                MarkdownTokenTypes.BACKTICK,
                MarkdownTokenTypes.DOUBLE_QUOTE,
                MarkdownTokenTypes.SINGLE_QUOTE,
                MarkdownTokenTypes.EOL,
                MarkdownTokenTypes.WHITE_SPACE,
                MarkdownTokenTypes.COLON,
                MarkdownTokenTypes.EMPH,
                GFMTokenTypes.TILDE,
                GFMTokenTypes.DOLLAR -> {
                    append(node.getTextInNode(sourceText))
                }

                MarkdownElementTypes.SHORT_REFERENCE_LINK,
                MarkdownElementTypes.LINK_DEFINITION,
                MarkdownElementTypes.FULL_REFERENCE_LINK -> {
                    append(node.getTextInNode(sourceText))
                }

                else -> {
                    withStyle(SpanStyle(color = Color.Cyan)) {
                        append("[${node.type.name}]{\n")
                    }
                    for (child in node.children) {
                        append(annotateText(state, child))
                    }
                    withStyle(SpanStyle(color = Color.Cyan)) {
                        append("\n}")
                    }
                }
            }
        }
    } catch (e: Exception) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFFFF0000), background = Color(0xFF000000))) {
                append("[${node.type.name}] Error: ${e.message}")
            }

            Log.e("JBMRenderer", "Error rendering node: ${node.type.name}", e)
        }
    }
}

@Composable
private fun JBMText(node: ASTNode, modifier: Modifier) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val mdState = LocalJBMarkdownTreeState.current
    val annotatedText = remember(node) { annotateText(mdState, node) }
    val colours = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val shouldConsumeTap = handler@{ offset: Int ->
        if (!mdState.linksClickable) {
            return@handler false
        }

        JBMAnnotations.entries.filter { it.clickable }.map { it.tag }.forEach { tag ->
            if (annotatedText.getStringAnnotations(
                    tag = tag,
                    start = offset,
                    end = offset
                ).isNotEmpty()
            ) {
                return@handler true
            }
        }

        return@handler false
    }

    val onClick = handler@{ offset: Int ->
        if (mdState.linksClickable) {
            JBMAnnotations.entries.filter { it.clickable }.map { it.tag }.forEach { tag ->
                val annotations = annotatedText.getStringAnnotations(
                    tag = tag,
                    start = offset,
                    end = offset
                )
                annotations.forEach { annotation ->
                    val item = annotation.item
                    when (tag) {
                        JBMAnnotations.URL.tag -> {
                            try {
                                val uri = item.toUri()
                                if (uri.isInviteUri()) {
                                    scope.launch {
                                        Intent(context, InviteActivity::class.java).apply {
                                            data = uri
                                            context.startActivity(this)
                                        }
                                    }
                                    return@handler true
                                }
                            } catch (e: Exception) {
                                // no-op
                            }

                            val customTab = CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .setDefaultColorSchemeParams(
                                    CustomTabColorSchemeParams.Builder()
                                        .setToolbarColor(colours.surfaceContainer.toArgb())
                                        .build()
                                )
                                .build()

                            try {
                                customTab.launchUrl(context, item.toUri())
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.link_type_no_intent),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            return@handler true
                        }
                    }
                }
            }
        }

        return@handler false
    }

    val onLongClick = handler@{ offset: Int ->
        if (mdState.linksClickable) {
            annotatedText.getStringAnnotations(
                tag = Annotations.URL.tag,
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                scope.launch {
                    ActionChannel.send(Action.LinkInfo(annotation.item))
                }

                return@handler true
            }

            annotatedText.getStringAnnotations(
                tag = Annotations.UserMention.tag,
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                scope.launch {
                    ActionChannel.send(
                        Action.OpenUserSheet(
                            annotation.item,
                            mdState.currentServer
                        )
                    )
                }

                return@handler true
            }

            annotatedText.getStringAnnotations(
                tag = Annotations.ChannelMention.tag,
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                scope.launch {
                    ActionChannel.send(Action.SwitchChannel(annotation.item))
                }

                return@handler true
            }

            annotatedText.getStringAnnotations(
                tag = Annotations.CustomEmote.tag,
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                scope.launch {
                    ActionChannel.send(Action.EmoteInfo(annotation.item))
                }

                return@handler true
            }
        }

        return@handler false
    }

    Text(
        text = annotatedText,
        onTextLayout = { layoutResult = it },
        maxLines = if (mdState.singleLine) 1 else Int.MAX_VALUE,
        overflow = if (mdState.singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier.pointerInput(onClick, onLongClick) {
            detectTapGesturesConditionalConsume(
                onTap = { pos ->
                    val index =
                        layoutResult?.getOffsetForPosition(pos)
                            ?: return@detectTapGesturesConditionalConsume
                    onClick(index)
                },
                onLongPress = { pos ->
                    val index =
                        layoutResult?.getOffsetForPosition(pos)
                            ?: return@detectTapGesturesConditionalConsume
                    onLongClick(index)
                },
                shouldConsumeTap = { pos ->
                    val index =
                        layoutResult?.getOffsetForPosition(pos)
                            ?: return@detectTapGesturesConditionalConsume false
                    shouldConsumeTap(index)
                }
            )
        },
        inlineContent = mapOf(
            JBMAnnotations.Checkbox.tag to InlineTextContent(
                placeholder = Placeholder(
                    width = LocalTextStyle.current.fontSize * 1.5,
                    height = LocalTextStyle.current.fontSize * 1.5,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                ),
                children = { alternateText ->
                    val isCheck = alternateText == "✅"

                    with(LocalDensity.current) {
                        Canvas(modifier = Modifier.size((LocalTextStyle.current.fontSize * 1.5).toDp())) {
                            drawRoundRect(
                                color = if (isCheck) colours.primaryContainer else colours.surfaceContainer,
                                cornerRadius = CornerRadius(size.width * 0.1f),
                                topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                                size = size.copy(
                                    width = size.width * 0.8f,
                                    height = size.height * 0.8f
                                )
                            )

                            if (isCheck) {
                                drawPath(
                                    path = Path().apply {
                                        moveTo(size.width * 0.8f, size.height * 0.3f)
                                        lineTo(size.width * 0.4f, size.height * 0.7f)
                                        lineTo(size.width * 0.2f, size.height * 0.5f)
                                    },
                                    color = colours.onPrimaryContainer,
                                    style = Stroke(width = size.width * 0.1f)
                                )
                            }
                        }
                    }
                }
            ),
            JBMAnnotations.CustomEmote.tag to InlineTextContent(
                placeholder = Placeholder(
                    width = LocalTextStyle.current.fontSize * 1.5,
                    height = LocalTextStyle.current.fontSize * 1.5,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                ),
            ) { id ->
                val emote = RevoltAPI.emojiCache[id]
                if (emote == null) {
                    scope.launch {
                        try {
                            RevoltAPI.emojiCache[id] = fetchEmoji(id)
                        } catch (e: Exception) {
                            // no-op
                        }
                    }
                    return@InlineTextContent
                } else {
                    with(LocalDensity.current) {
                        RemoteImage(
                            url = "$REVOLT_FILES/emojis/${id}/emoji.gif",
                            description = emote.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width((LocalTextStyle.current.fontSize * 1.5).toDp())
                                .height((LocalTextStyle.current.fontSize * 1.5).toDp())
                        )
                    }
                }
            }
        )
    )
}

private fun annotateHighlights(
    source: String,
    highlights: List<CodeHighlight>
): AnnotatedString {
    val highlightStyles = highlights.mapNotNull {
        when (it) {
            is BoldHighlight -> AnnotatedString.Range(
                SpanStyle(fontWeight = FontWeight.Bold),
                it.location.start,
                it.location.end
            )

            is ColorHighlight -> {
                AnnotatedString.Range(
                    SpanStyle(color = Color(0xFF000000 or it.rgb.toLong())),
                    it.location.start,
                    it.location.end
                )
            }

            else -> null
        }
    }

    return AnnotatedString(source, spanStyles = highlightStyles)
}

val languageAliases = mapOf(
    "js" to SyntaxLanguage.JAVASCRIPT,
    "ts" to SyntaxLanguage.TYPESCRIPT,
    "rs" to SyntaxLanguage.RUST,
    "cs" to SyntaxLanguage.CSHARP,
    "py" to SyntaxLanguage.PYTHON,
    "pl" to SyntaxLanguage.PERL,
    "bash" to SyntaxLanguage.SHELL,
    "sh" to SyntaxLanguage.SHELL,
    "py" to SyntaxLanguage.PYTHON,
    "coffee" to SyntaxLanguage.COFFEESCRIPT,
    "bad" to SyntaxLanguage.PHP // had to
)

val languageDisplayNamedResource = mapOf(
    SyntaxLanguage.DEFAULT to R.string.programming_language_default,
    SyntaxLanguage.C to R.string.programming_language_c,
    SyntaxLanguage.CPP to R.string.programming_language_cpp,
    SyntaxLanguage.DART to R.string.programming_language_dart,
    SyntaxLanguage.JAVA to R.string.programming_language_java,
    SyntaxLanguage.KOTLIN to R.string.programming_language_kotlin,
    SyntaxLanguage.RUST to R.string.programming_language_rust,
    SyntaxLanguage.CSHARP to R.string.programming_language_csharp,
    SyntaxLanguage.COFFEESCRIPT to R.string.programming_language_coffeescript,
    SyntaxLanguage.JAVASCRIPT to R.string.programming_language_javascript,
    SyntaxLanguage.PERL to R.string.programming_language_perl,
    SyntaxLanguage.PYTHON to R.string.programming_language_python,
    SyntaxLanguage.RUBY to R.string.programming_language_ruby,
    SyntaxLanguage.SHELL to R.string.programming_language_shell,
    SyntaxLanguage.SWIFT to R.string.programming_language_swift,
    SyntaxLanguage.TYPESCRIPT to R.string.programming_language_typescript,
    SyntaxLanguage.GO to R.string.programming_language_go,
    SyntaxLanguage.PHP to R.string.programming_language_php,
)

@Composable
private fun JBMCodeBlockContent(node: ASTNode, modifier: Modifier) {
    val state = LocalJBMarkdownTreeState.current

    val uiMode = LocalConfiguration.current.uiMode
    val systemIsDark =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val themeIsDark =
        remember(LoadedSettings.theme) { isThemeDark(LoadedSettings.theme, systemIsDark) }

    val codeFenceLanguage = remember(node) {
        node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.getTextInNode(state.sourceText)?.toString()
    }
    val codeFenceContent = remember(node) {
        node.children
            .filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || it.type == MarkdownTokenTypes.EOL }
            .joinToString("") {
                it.getTextInNode(state.sourceText).toString()
            }
            .trim()
    }
    val annotatedContent = remember(codeFenceLanguage, codeFenceContent) {
        val canAnnotate = codeFenceLanguage != null
        val language = codeFenceLanguage?.let {
            languageAliases[it] ?: SyntaxLanguage.getByName(it)
        }
        val shouldAnnotate = language != null

        if (canAnnotate && shouldAnnotate) {
            buildAnnotatedString {
                val highlights = Highlights.Builder().apply {
                    code(codeFenceContent)
                    language(language ?: SyntaxLanguage.DEFAULT)
                    theme(SyntaxThemes.atom(themeIsDark))
                }.build()
                append(annotateHighlights(codeFenceContent, highlights.getHighlights()))
            }
        } else {
            buildAnnotatedString {
                append(codeFenceContent)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp)
    ) {
        Text(
            text = languageDisplayNamedResource[
                languageAliases[codeFenceLanguage]
                    ?: SyntaxLanguage.getByName(codeFenceLanguage ?: "")
            ]?.let {
                stringResource(it)
            } ?: stringResource(R.string.programming_language_default),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Box(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = annotatedContent,
                fontFamily = FragmentMono,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun JBMBlock(node: ASTNode, modifier: Modifier, nestingCounter: Int = 0) {
    val state = LocalJBMarkdownTreeState.current
    val colorScheme = MaterialTheme.colorScheme

    when (node.type) {
        MarkdownElementTypes.PARAGRAPH,
        MarkdownElementTypes.HTML_BLOCK,
        MarkdownElementTypes.LINK_DEFINITION,
        MarkdownTokenTypes.WHITE_SPACE -> {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(
                    fontSize = LocalTextStyle.current.fontSize * state.fontSizeMultiplier
                )
            ) {
                JBMText(node, modifier)
            }
        }

        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (state.embedded) LocalTextStyle.current.fontSize
                    else when (node.type) {
                        MarkdownElementTypes.ATX_1 -> 32.sp * state.fontSizeMultiplier
                        MarkdownElementTypes.ATX_2 -> 24.sp * state.fontSizeMultiplier
                        MarkdownElementTypes.ATX_3 -> 20.sp * state.fontSizeMultiplier
                        MarkdownElementTypes.ATX_4 -> 16.sp * state.fontSizeMultiplier
                        MarkdownElementTypes.ATX_5 -> 14.sp * state.fontSizeMultiplier
                        else -> 12.sp * state.fontSizeMultiplier
                    }
                )
            ) {
                if (node.startOffset != 0) {
                    Spacer(Modifier.height(8.dp))
                }
                JBMText(node, modifier)
                Spacer(Modifier.height(4.dp))
            }
        }

        MarkdownElementTypes.ORDERED_LIST,
        MarkdownElementTypes.UNORDERED_LIST -> {
            CompositionLocalProvider(
                LocalJBMarkdownTreeState provides state.copy(
                    listDepth = state.listDepth + 1
                )
            ) {
                JBMText(node, modifier)
            }
        }

        MarkdownTokenTypes.EOL -> {
            Spacer(Modifier.height(4.dp))
        }

        MarkdownElementTypes.CODE_FENCE -> {
            JBMCodeBlockContent(node, modifier)
        }

        MarkdownElementTypes.BLOCK_QUOTE -> {
            if (LocalJBMarkdownTreeState.current.embedded) {
                node.children.getOrNull(0)?.let {
                    JBMBlock(it, modifier)
                }
            } else {
                Column(
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(colorScheme.surfaceContainer.copy(alpha = 0.5f))
                            drawLine(
                                colorScheme.primary,
                                Offset.Zero,
                                Offset(0f, size.height),
                                strokeWidth = 16f
                            )
                        }
                        .padding(8.dp)
                        .padding(start = 4.dp)
                ) {
                    if (nestingCounter < 5) {
                        node.children.map {
                            JBMBlock(it, modifier, nestingCounter = nestingCounter + 1)
                        }
                    }
                }
            }
        }

        MarkdownTokenTypes.BLOCK_QUOTE -> {
            if (LocalJBMarkdownTreeState.current.embedded) {
                node.children.getOrNull(0)?.let {
                    JBMBlock(it, modifier)
                }
            } else {
                node.children.map {
                    JBMBlock(it, modifier)
                }
            }
        }

        else -> {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFFF7F50))) {
                        append("[Unknown block type ${node.type.name}]")
                    }
                },
                maxLines = if (state.singleLine) 1 else Int.MAX_VALUE,
                overflow = if (state.singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = modifier
            )
        }
    }
}