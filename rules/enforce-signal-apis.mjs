import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * The entire Angular client has been migrated to signal-based APIs. Legacy decorators
 * (@Input, @Output, @ViewChild, @ViewChildren, @ContentChild, @ContentChildren) must not be
 * reintroduced anywhere in the client code under src/main/webapp/app (including co-located specs).
 *
 * This rule used to be scoped to an explicit allowlist of migrated modules; now that the migration
 * is complete it applies to all client code, so no module list needs to be maintained.
 */
const FORBIDDEN_DECORATORS = new Set(['Input', 'Output', 'ViewChild', 'ViewChildren', 'ContentChild', 'ContentChildren']);

const SIGNAL_REPLACEMENTS = {
    Input: 'input() / input.required()',
    Output: 'output()',
    ViewChild: 'viewChild() / viewChild.required()',
    ViewChildren: 'viewChildren()',
    ContentChild: 'contentChild() / contentChild.required()',
    ContentChildren: 'contentChildren()',
};

export default createRule({
    name: 'enforce-signal-apis',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Forbid legacy Angular decorators (@Input, @Output, @ViewChild, @ViewChildren, @ContentChild, @ContentChildren) anywhere in the client code, which has been fully migrated to Angular signal-based APIs.',
        },
        messages: {
            forbiddenDecorator: "Legacy decorator '@{{decoratorName}}' is not allowed; the client uses signal-based APIs. Use {{replacement}} instead.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        // Normalize Windows backslashes so the client-code path check works across operating systems.
        const filename = (context.filename ?? context.getFilename()).replaceAll('\\', '/');

        // Apply to all client code (including co-located test/spec files) under the Angular app directory.
        if (!filename.includes('src/main/webapp/app/')) {
            return {};
        }

        return {
            Decorator(node) {
                let decoratorName;
                const expr = node.expression;

                if (expr.type === 'CallExpression' && expr.callee.type === 'Identifier') {
                    decoratorName = expr.callee.name;
                } else if (expr.type === 'Identifier') {
                    decoratorName = expr.name;
                }

                if (decoratorName && FORBIDDEN_DECORATORS.has(decoratorName)) {
                    context.report({
                        node,
                        messageId: 'forbiddenDecorator',
                        data: {
                            decoratorName,
                            replacement: SIGNAL_REPLACEMENTS[decoratorName],
                        },
                    });
                }
            },
        };
    },
});
