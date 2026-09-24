import { CssSyntaxError } from 'postcss';
import scss from 'postcss-scss';
import { plugin as engine } from './design-system/plugin.mjs';
import { createAngularMetadataReader } from './angular-design-system-host.mjs';
import { evaluateStylesheet, evaluateAppliedClassSelectors } from './angular-design-system-stylelint.mjs';
import { checkPrivateClasses } from './design-system-private-classes-stylelint.mjs';

/** Inline and external stylesheets share selector analysis and upstream property policy. */
export function createInlineStylesRule(getIndex, theme) {
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
                stylesheet: '{{message}} (inline stylesheet line {{line}}, column {{column}})',
                unreadable: 'Cannot verify Angular inline styles. Use statically readable same-file strings or a styleUrl checked by Stylelint.',
                unreadableMetadata:
                    'Cannot inspect Angular component metadata, so inline templates and styles cannot be checked. Use a direct metadata object or same-file constants with statically known keys and spreads.',
                invalidSyntax: 'Cannot parse inline CSS/SCSS: {{reason}} (stylesheet line {{line}}, column {{column}}). Fix the syntax before design-system checks can run.',
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
                            context.report({ node: call.arguments[0] ?? decorator, messageId: 'unreadableMetadata' });
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
                                } catch (error) {
                                    if (!(error instanceof CssSyntaxError)) throw error;
                                    context.report({
                                        node: value.loc ? value : expression,
                                        messageId: 'invalidSyntax',
                                        data: { reason: error.reason, line: error.line, column: error.column },
                                    });
                                    return;
                                }
                                const report = ({ message, node }) =>
                                    context.report({
                                        node: value.loc ? value : expression,
                                        messageId: 'stylesheet',
                                        data: { message, line: node.source.start.line, column: node.source.start.column },
                                    });
                                const index = getIndex();
                                evaluateStylesheet(sheet, index, propertyOptions, report);
                                evaluateAppliedClassSelectors(sheet, index, { theme, propertyOptions, privateClassPrefix }, report);
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
