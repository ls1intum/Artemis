/**
 * @fileoverview Custom ESLint rule to disallow the use of deprecated HttpClientTestingModule.
 */

"use strict";

module.exports = {
    meta: {
        type: "problem", // Indicates this rule relates to a possible error
        docs: {
            description: "Disallow the use of deprecated HttpClientTestingModule",
            category: "Best Practices",
            recommended: true, // Whether this rule is recommended in ESLint configurations
        },
        messages: {
            avoidHttpClientTestingModule: "'HttpClientTestingModule' is deprecated. Avoid using it.",
        },
        schema: [], // No options for this rule
    },

    create(context) {
        return {
            ImportDeclaration(node) {
                if (
                    node.source.value === "@angular/common/http/testing" &&
                    node.specifiers.some(
                        (specifier) => specifier.imported && specifier.imported.name === "HttpClientTestingModule"
                    )
                ) {
                    context.report({
                        node,
                        messageId: "avoidHttpClientTestingModule",
                    });
                }
            },
        };
    },
};
