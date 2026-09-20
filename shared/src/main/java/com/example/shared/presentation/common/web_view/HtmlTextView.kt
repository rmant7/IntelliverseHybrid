package com.example.shared.presentation.common.web_view

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlTextView(
    modifier: Modifier,
    title: String? = null,
    htmlContent: String,
    isEditable: Boolean,
    textDirection: LayoutDirection?,
    isReload: Boolean = false,
    onWebViewCreated: (WebView) -> Unit,
) {

    val isNightMode = isSystemInDarkTheme()
    val htmlContentWithLineBreak = preserveMathBlocksAndAddLineBreaks(htmlContent)

    fun loadHtml(webView: WebView) {
        webView.apply {
            // Clear WebView cache if clearing cache on app start doesn't help.
            // clearCache(false)
            // clearHistory()
            // clearFormData()

            loadDataWithBaseURL(
                null,
                createKatexHtml(
                    title = title,
                    content = htmlContentWithLineBreak,
                    isEditable = isEditable,
                    textDirection = getTextDirStr(textDirection),
                    isNightMode = isNightMode
                ),
                "text/html",
                "utf-8",
                null
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true

                // Zooms out if content's width is greater than container's one
                settings.loadWithOverviewMode = true

                // Loads the WebView completely zoomed out
                // settings.useWideViewPort = true

                isVerticalScrollBarEnabled = true
                onWebViewCreated(this)
                // Load the HTML content with the scripts and contenteditable div
                loadHtml(this)
            }
        },
        update = { webView ->

            if (isReload) {
                loadHtml(webView)
            }

            webView.evaluateJavascript(
                "refreshPage(${isEditable}, '${getTextDirStr(textDirection)}')"
            ) {}

        }

    )
}

private fun getTextDirStr(layoutDirection: LayoutDirection?): String {
    return when (layoutDirection) {
        LayoutDirection.Rtl -> "rtl"
        LayoutDirection.Ltr -> "ltr"
        else -> "auto"
    }
}

// Remove Kotlin String formatting
fun cleanHtmlStr(str: String): String {
    if (str.isBlank()) {
        return ""
    }
    // Decode the JSON string
    val cleanedStr = str.substring(1, str.length - 1) // Remove enclosing quotes
        .replace("\\n", "\n") // Remove newline characters
        .replace("\\u003C", "<") // Decode <
        .replace("\\u003E", ">") // Decode >
        .replace("\\\"", "\"") // Decode escaped quotes
        .trim()
    return cleanedStr
}

// Katex Math rendering
private fun createKatexHtml(
    title: String?,
    content: String,
    isEditable: Boolean,
    textDirection: String,
    isNightMode: Boolean
): String {

    // Should be converted to css color scheme
    // instead of hard coded colours in css style
    /*
    val background = MaterialTheme.colorScheme.background
    val fontColor = MaterialTheme.colorScheme.primary
    */
    val background: String
    val fontColor: String
    if (isNightMode) {
        background = "#14141C"
        fontColor = "white"
    } else {
        background = "white"
        fontColor = "black"
    }

    return """
    <!DOCTYPE html>
    <head>
       <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css" integrity="sha384-nB0miv6/jRmo5UMMR1wu3Gz6NLsoTkbqJghGIsx//Rlm+ZU03BU6SQNC66uf4l5+" crossorigin="anonymous">
       <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js" integrity="sha384-7zkQWkzuo3B5mTepMUcHkMB5jZaolc2xDwL6VFqjFALcbeS9Ggm/Yr2r3Dy4lfFg" crossorigin="anonymous"></script>
       <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js" integrity="sha384-43gviWU0YVjaDtb/GhzOouOXtZMP/7XUzwPTstBeZFe/+rCMvRwr4yROQP43s0Xk" crossorigin="anonymous"></script>
       <script>
            function getContent() {
                return document.getElementById('editable').innerHTML;  
            }
                
            function setContent(content) {
                document.getElementById('editable').innerHTML = content;
            }
            
            function refreshPage(isEditable, textDirection){
                setEditable(isEditable)
                setTextDir(textDirection)
            }
                
            function setEditable(edit) {
                document.getElementById('editable').setAttribute('contenteditable', edit);
            }
            
            function renderMath(){
                renderMathInElement(document.getElementById("editable").innerHTML);
            }
            
            function setTextDir(dir){
                 document.getElementById('textView').setAttribute('dir', dir);
            }
            
            function getPlainMathAndUrls() {
                const editableDiv = document.getElementById('editable');
                let resultText = "";

                // Walk through all child nodes
                function processNode(node) {
                    if (node.nodeType === Node.TEXT_NODE) {
                        // Append normal text as-is
                        resultText += node.textContent;
                    } else if (node.tagName === "A") {
                        // Extract URL from <a> tag
                        const href = node.getAttribute("href");
                        resultText += href;
                    } else if (node.tagName === "BR") {
                       // Replace <br> elements with new lines
                        resultText += "\n";
                    }
                       else if (node.classList && node.classList.contains("katex")) {
                        // Extract only the rendered visible math, ignore KaTeX source content
                        const renderedMath = node.querySelector(".katex-html");
                        if (renderedMath) {
                            resultText += renderedMath.textContent;
                        }
                    } else if (node.childNodes) {
                        // Recursively process child nodes
                        for (const child of node.childNodes) {
                            processNode(child);
                        }
                    }
                }

                processNode(editableDiv);

                // Final cleanup for spacing, quotes and duplicates
                return resultText.trim();
            } 
            
            document.addEventListener("DOMContentLoaded", function() {
                renderMathInElement(document.body, {
                    // customised options
                    // • auto-render specific keys, e.g.:
                    delimiters: [
                        {left: '$$', right: '$$', display: true},
                        {left: '$', right: '$', display: true},
                        {left: '\\(', right: '\\)', display: true},
                        {left: '\\[', right: '\\]', display: true}
                    ],
                    // • rendering keys, e.g.:
                    throwOnError : false
                });
            });
            
       </script>
       
       <style>
       
       body {
        background-color: $background;
        color: $fontColor;
        font-size: 20px;
        overflow-y: scroll;
       }
        #textView, #editable {
        word-wrap: break-word;
        overflow-wrap: break-word;
        word-break: break-word;
        overflow-x: auto;
        }
        
       </style>
       
    </head>
        <body>
            <div id="textView" dir="$textDirection">
            <h2 dir="auto">$title</h2>
                <div id="editable" contenteditable="$isEditable">
                    $content
                </div>
            </div>
        </body>
    </html>
    """.trimIndent()

}

private fun preserveMathBlocksAndAddLineBreaks(input: String): String {
    val mathRegex = Regex("\\$\\$?(.*?)\\$\\$?", RegexOption.DOT_MATCHES_ALL)
    val result = StringBuilder()
    var lastIndex = 0

    for (match in mathRegex.findAll(input)) {
        val beforeMath = input.substring(lastIndex, match.range.first)
        result.append(beforeMath.replace("\n", "<br>"))
        result.append(match.value) // math block untouched
        lastIndex = match.range.last + 1
    }

    if (lastIndex < input.length) {
        result.append(input.substring(lastIndex).replace("\n", "<br>"))
    }

    return result.toString()
}
