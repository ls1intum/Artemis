import { createAngularMetadataReader } from './angular-design-system-host.mjs';

/** Keep unsupported inline metadata explicit rather than inventing a replacement Angular processor. */
export const requireLintableTemplates = {
    meta: {
        type: 'problem',
        docs: { description: 'Require inline Angular templates to use metadata the native Angular ESLint processor extracts.' },
        schema: [],
        messages: {
            unsupported:
                'Angular ESLint cannot reliably extract this inline template. Use an external templateUrl or canonical @Component({ template: "..." }) with a literal template.',
        },
    },
    create(context) {
        if (context.sourceCode.ast.templateNodes) return {};
        const { angularName, valueOf, property } = createAngularMetadataReader(context.sourceCode.ast, context.sourceCode.parserServices);
        return {
            ClassDeclaration(declaration) {
                for (const decorator of declaration.decorators ?? []) {
                    const call = decorator.expression;
                    if (call.type !== 'CallExpression' || angularName(call.callee) !== 'Component') continue;
                    const metadata = valueOf(call.arguments[0]);
                    // The inline-stylesheet rule already reports metadata whose contents cannot be verified.
                    if (metadata?.type !== 'ObjectExpression' || metadata.properties.some((entry) => entry.type !== 'Property' || entry.computed)) continue;
                    const template = property(metadata, 'template');
                    if (!template) continue;
                    const raw = call.arguments[0];
                    const direct =
                        raw?.type === 'ObjectExpression' &&
                        raw.properties.find((entry) => entry.type === 'Property' && !entry.computed && entry.key.type === 'Identifier' && entry.key.name === 'template');
                    const literal =
                        (template.value.type === 'Literal' && typeof template.value.value === 'string') ||
                        (template.value.type === 'TemplateLiteral' && !template.value.expressions.length);
                    const callOpen = context.sourceCode.getTokenAfter(call.callee);
                    const argumentStart = context.sourceCode.getTokenAfter(callOpen);
                    if (
                        call.callee.type !== 'Identifier' ||
                        call.callee.name !== 'Component' ||
                        call.arguments.length !== 1 ||
                        argumentStart.range[0] !== raw.range[0] ||
                        direct !== template ||
                        !literal ||
                        property(metadata, 'templateUrl')
                    ) {
                        context.report({ node: template, messageId: 'unsupported' });
                    }
                }
            },
        };
    },
};
