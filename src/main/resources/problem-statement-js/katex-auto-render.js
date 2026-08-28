/**
 * Client-side KaTeX auto-renderer for the server-rendered problem statement.
 *
 * The server emits <span class="katex-formula" data-formula="..." data-display-mode="..."> placeholders
 * instead of rendered math. This script runs after katex.min.js, walks those placeholders, and replaces
 * each with the rendered math. If KaTeX throws on a formula, the raw LaTeX source is shown as text
 * rather than failing loudly.
 */
(function () {
    'use strict';
    var formulas = document.querySelectorAll('.katex-formula');
    for (var i = 0; i < formulas.length; i++) {
        var el = formulas[i];
        var formula = el.getAttribute('data-formula');
        var displayMode = el.getAttribute('data-display-mode') === 'true';
        try {
            katex.render(formula, el, {
                displayMode: displayMode,
                throwOnError: false,
                output: 'html',
                // KaTeX leaves maxSize at Infinity, so `\rule{1000000000em}{1000000000em}` asks the consumer to lay
                // out a box no engine can afford. The Angular client sets the same bound in
                // `problem-statement-frame.util.ts`; this script is the path a consumer takes when it asks for the
                // document with includeJs, which is the default, so the limit has to be here as well or the
                // standalone consumer is the only one left without it.
                maxSize: 100,
                maxExpand: 1000
            });
        } catch (e) {
            el.textContent = formula;
        }
    }
})();
