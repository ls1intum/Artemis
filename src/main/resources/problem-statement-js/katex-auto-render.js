(function() {
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
