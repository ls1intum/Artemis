import scss from 'postcss-scss';
import { plugin as engine } from './design-system/plugin.mjs';
import { createAngularMetadataReader } from './angular-design-system-host.mjs';
import { evaluateStylesheet } from './angular-design-system-stylelint.mjs';
import { checkPrivateClasses } from './design-system-private-classes-stylelint.mjs';

/** Inline and external stylesheets share selector analysis and upstream property policy. */
export function createInlineStylesRule(getIndex) {
    return {
        meta: {
            type: 'problem',
            docs: { description: 'Check Angular component inline styles against the design-system stylesheet contract.' },
            schema: [
                {
                    type: 'object',
                    properties: { propertyOptions: engine.rules['no-inline-styles'].meta.schema[0], privateClassPrefix: { type: 'string', minLength: 1 } },
                    additionalProperties: false,
                },
            ],
            messages: {
                stylesheet: '{{message}}',
                unreadable: 'Cannot verify Angular inline styles. Use statically readable same-file strings or a styleUrl checked by Stylelint.',
            },
        },
        create(context) {
            if (context.sourceCode.ast.templateNodes) return {};
            const { angularName, valueOf, property } = createAngularMetadataReader(context.sourceCode.ast, context.sourceCode.parserServices);
            const { propertyOptions = {}, privateClassPrefix } = context.options[0] ?? {};
            return {
                ClassDeclaration(declaration) {
                    for (const decorator of declaration.decorators ?? []) {
                        const call = decorator.expression;
                        if (call.type !== 'CallExpression' || angularName(call.callee) !== 'Component') continue;
                        const metadata = valueOf(call.arguments[0]);
                        if (metadata?.type !== 'ObjectExpression' || metadata.properties.some((entry) => entry.type !== 'Property' || entry.computed)) {
                            context.report({ node: call.arguments[0] ?? decorator, messageId: 'unreadable' });
                            continue;
                        }
                        const styles = property(metadata, 'styles');
                        if (!styles) continue;
                        const visited = new Set();
                        const inspect = (expression) => {
                            const value = valueOf(expression);
                            if (!value || visited.has(value)) {
                                context.report({ node: expression ?? styles, messageId: 'unreadable' });
                                return;
                            }
                            visited.add(value);
                            if (value.type === 'ArrayExpression') {
                                for (const element of value.elements) inspect(element?.type === 'SpreadElement' ? element.argument : element);
                            } else if (value.type === 'Literal' && typeof value.value === 'string') {
                                let sheet;
                                try {
                                    sheet = scss.parse(value.value, { from: context.filename });
                                } catch {
                                    context.report({ node: expression, messageId: 'unreadable' });
                                    return;
                                }
                                const report = ({ message }) => context.report({ node: value.loc ? value : expression, messageId: 'stylesheet', data: { message } });
                                evaluateStylesheet(sheet, getIndex(), propertyOptions, report);
                                if (privateClassPrefix) checkPrivateClasses(sheet, privateClassPrefix, report);
                            } else context.report({ node: expression, messageId: 'unreadable' });
                            visited.delete(value);
                        };
                        inspect(styles.value);
                    }
                },
            };
        },
    };
}
