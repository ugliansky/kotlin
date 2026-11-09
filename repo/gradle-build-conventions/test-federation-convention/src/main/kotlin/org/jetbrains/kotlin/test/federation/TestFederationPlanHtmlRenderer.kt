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
                <details>
                    <summary><h2>What are test subsets?</h2></summary>
                    <br/>
                    The "test subset" is a concept introduced by Test Federation. It's basically a subset of tests selected by a given test task.
                    <br/>
                    <br/>
                    You may think of it as of the 3rd level of the filtering hierarchy:
                    <br/>
                    <br/>
                    <img src="repo/gradle-build-conventions/test-federation-convention/img/test-federation-filtering.png" alt="How Test Federation filters tests">
                </details>
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
            code { font-family: monospace; font-size: 0.9em; }
            h2 { font-size: 1.15em; margin: 24px 0 8px; }
            h2:first-child { margin-top: 0; }
            details { margin: 24px 0 8px; }
            summary { cursor: pointer; user-select: none; list-style: none; }
            summary::-webkit-details-marker { display: none; }
            summary h2 { display: inline; }
            summary::before { content: '▸'; display: inline-block; margin-right: 6px; transition: transform 0.15s; }
            details[open] summary::before { transform: rotate(90deg); }
            img { display: block; max-width: 600px; height: auto; padding: 12px; border: 1px solid var(--border); border-radius: 8px; box-sizing: border-box; }
            .chip { display: inline-block; padding: 1px 8px; border-radius: 999px; border: 1px solid; font-size: 0.9em; white-space: nowrap; }
            .chip-all-tests { background: var(--all-tests-bg); color: var(--all-tests-fg); border-color: var(--all-tests-border); }
            .chip-smoke-tests { background: var(--smoke-tests-bg); color: var(--smoke-tests-fg); border-color: var(--smoke-tests-border); }
            .chip-contract-tests { background: var(--contract-tests-bg); color: var(--contract-tests-fg); border-color: var(--contract-tests-border); }
            .chip[data-annotation] { position: relative; }
            .chip[data-annotation]::after {
                content: attr(data-annotation);
                position: absolute;
                bottom: calc(100% + 4px);
                left: 50%;
                transform: translateX(-50%);
                padding: 2px 8px;
                background: var(--fg);
                color: var(--bg);
                border-radius: 6px;
                font-family: monospace;
                font-size: 0.9em;
                white-space: nowrap;
                opacity: 0;
                transition: opacity 0.15s;
                pointer-events: none;
            }
            .chip[data-annotation]::before {
                content: '';
                position: absolute;
                bottom: calc(100% - 4px);
                left: 50%;
                transform: translateX(-50%);
                border: 4px solid transparent;
                border-top-color: var(--fg);
                opacity: 0;
                transition: opacity 0.15s;
                pointer-events: none;
            }
            .chip[data-annotation]:hover::after, .chip[data-annotation]:hover::before { opacity: 1; }
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
            val domain = if (row.isChanged) "<strong>$domainName</strong>" else domainName
            val subsets = row.testSubsets.joinToString(" + ") { it.toChipHtml() }
            appendLine("<tr><td>$domain</td><td>$subsets</td><td>${reasonHtml(row.reason)}</td></tr>")
        }
    }

    private fun reasonHtml(reason: String): String {
        val escapedReason = reason.escapeHtml()
        return if (reason.startsWith(MUST_RUN_ALL_TESTS_REASON_PREFIX)) "<code>$escapedReason</code>" else escapedReason
    }

    private fun TestSubset.toChipHtml(): String = when (this) {
        AllTests -> chipHtml("all-tests", "$ALL_TESTS_EMOJI all tests")
        SmokeTests -> chipHtml("smoke-tests", "$SMOKE_TESTS_EMOJI smoke tests", "@MustRunAlways")
        else -> contractTestsChipHtml(Domain.entries.first { contractTestsSubsetOf(it) == this })
    }

    private fun contractTestsChipHtml(domain: Domain): String =
        chipHtml("contract-tests", "$CONTRACT_TESTS_EMOJI contracts for ${domain.name}", "@MustRunOnChangesIn${domain.name}")

    private fun chipHtml(cssClass: String, content: String, annotationName: String? = null): String {
        val tooltipAttribute = annotationName?.let { " data-annotation='${it.escapeHtml()}'" }.orEmpty()
        return "<span class='chip chip-$cssClass'$tooltipAttribute>${content.escapeHtml()}</span>"
    }
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
