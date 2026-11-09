/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.testFederation

import org.jetbrains.kotlin.testFederation.TestSubset.AllTests
import org.jetbrains.kotlin.testFederation.TestSubset.SmokeTests

internal class TestFederationPlanHtmlRenderer(
    private val plan: TestFederationPlan
) {
    fun renderHtml(): String =
        // language=HTML
        """
        <!DOCTYPE html>
        <html lang='en'>
            <head>
                <meta charset="utf-8">
                <title>Test Federation</title>
                ${style()}
                ${supportDarkThemeScript()}
            </head>
            <body>
                <h2>Execution plan</h2>
                <table>
                    <tr><th>Domain</th><th>Test subsets</th><th>Reason</th></tr>
                    ${tableRows()}
                </table>
                <h2>What are test subsets?</h2>
            </body>
        </html>
        """.trimIndent()

    private fun style() =
        // language=HTML
        """
        <style>
            :root { --fg: #222; --bg: #fff; --border: #ccc; --header-bg: #f0f0f0; --all-tests-bg: #dbeafe; --all-tests-fg: #1e40af; --all-tests-border: #93c5fd; --smoke-tests-bg: #ffedd5; --smoke-tests-fg: #9a3412; --smoke-tests-border: #fdba74; --contract-tests-bg: #ede9fe; --contract-tests-fg: #5b21b6; --contract-tests-border: #c4b5fd; color-scheme: light; }
            :root.dark { --fg: #ddd; --bg: #1e1f22; --border: #4a4d52; --header-bg: #2b2d30; --all-tests-bg: #1c3a5e; --all-tests-fg: #9ec5ff; --all-tests-border: #39588a; --smoke-tests-bg: #43290f; --smoke-tests-fg: #ffb380; --smoke-tests-border: #7c4a1a; --contract-tests-bg: #322358; --contract-tests-fg: #c4b5fd; --contract-tests-border: #4c3a80; color-scheme: dark; }
            @media (prefers-color-scheme: dark) { :root:not(.light) { --fg: #ddd; --bg: #1e1f22; --border: #4a4d52; --header-bg: #2b2d30; --all-tests-bg: #1c3a5e; --all-tests-fg: #9ec5ff; --all-tests-border: #39588a; --smoke-tests-bg: #43290f; --smoke-tests-fg: #ffb380; --smoke-tests-border: #7c4a1a; --contract-tests-bg: #322358; --contract-tests-fg: #c4b5fd; --contract-tests-border: #4c3a80; color-scheme: dark; } }
            body { font-family: sans-serif; margin: 16px; color: var(--fg); background: var(--bg); }
            table { border-collapse: collapse; }
            th, td { border: 1px solid var(--border); padding: 4px 12px; text-align: left; }
            th { background: var(--header-bg); font-weight: normal; }
            h2 { font-size: 1.15em; margin: 24px 0 8px; }
            h2:first-child { margin-top: 0; }
            img { display: block; max-width: 600px; height: auto; padding: 12px; border: 1px solid var(--border); border-radius: 8px; box-sizing: border-box; }
            .chip { display: inline-block; padding: 1px 8px; border-radius: 999px; border: 1px solid; font-size: 0.9em; white-space: nowrap; }
            .chip-all-tests { background: var(--all-tests-bg); color: var(--all-tests-fg); border-color: var(--all-tests-border); }
            .chip-smoke-tests { background: var(--smoke-tests-bg); color: var(--smoke-tests-fg); border-color: var(--smoke-tests-border); }
            .chip-contract-tests { background: var(--contract-tests-bg); color: var(--contract-tests-fg); border-color: var(--contract-tests-border); }
        </style>
        """.trimIndent()

    private fun supportDarkThemeScript() =
        // language=HTML
        """
        <script>
            (function () {
              try {
                const parentDocument = window.parent.document;
                const background = window.parent.getComputedStyle(parentDocument.body).backgroundColor;
                const channels = background.match(/\d+(\.\d+)?/g);
                if (!channels || channels.length < 3 || (channels.length > 3 && Number(channels[3]) === 0)) return;
                const luminance = 0.299 * channels[0] + 0.587 * channels[1] + 0.114 * channels[2];
                document.documentElement.classList.add(luminance < 128 ? 'dark' : 'light');
              } catch (e) {}
            })();
        </script>
        """.trimIndent()

    private fun tableRows() = buildString {
        plan.entries.forEach { row ->
            val domainName = row.domain.name.escapeHtml()
            val domain = if (row.isChanged) "<strong>$domainName</strong>*" else domainName
            val subsets = row.testSubsets.joinToString(" + ") { it.toChipHtml() }
            appendLine("<tr><td>$domain</td><td>$subsets</td><td>${row.reason.escapeHtml()}</td></tr>")
        }
    }

    private fun TestSubset.toChipHtml(): String = when (this) {
        AllTests -> chipHtml("all-tests", "$ALL_TESTS_EMOJI all tests")
        SmokeTests -> chipHtml("smoke-tests", "$SMOKE_TESTS_EMOJI smoke tests")
        else -> contractTestsChipHtml(Domain.entries.first { contractTestsSubsetOf(it) == this }.name)
    }

    private fun contractTestsChipHtml(domainName: String): String =
        chipHtml("contract-tests", "$CONTRACT_TESTS_EMOJI contracts for $domainName")

    private fun chipHtml(cssClass: String, content: String): String =
        "<span class='chip chip-$cssClass'>${content.escapeHtml()}</span>"
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
