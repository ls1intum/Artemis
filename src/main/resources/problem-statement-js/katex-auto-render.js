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
                output: 'html'
            });
        } catch (e) {
            el.textContent = formula;
        }
    }
})();
