import { createAngularHostReader } from './angular-design-system-host.mjs';
import { classExpressionValues, classValues } from './angular-design-system-expressions.mjs';

// Package privacy is an Artemis invariant, separate from upstream appearance classification.
export default {
    meta: {
        type: 'problem',
        schema: [],
        messages: { internal: 'Do not target private TUM UI classes ({{value}}). Use public component inputs or theme properties.' },
    },
    create(context) {
        const check = (value, node) => {
            for (const token of value.split(/\s+/).filter(Boolean)) {
                if (/^tum-ui-/.test(token) || (token.includes('[') && /(?:\.tum-ui-|\[tumUi[A-Z]|\[class[^\]]*tum-ui-)/.test(token))) {
                    context.report({ node, messageId: 'internal', data: { value: token } });
                }
            }
        };
        if (!context.sourceCode.ast.templateNodes) {
            const hosts = createAngularHostReader(context);
            return {
                ClassDeclaration(declaration) {
                    for (const { records } of hosts(declaration)) {
                        for (const record of records) {
                            const name = record.name.replace(/^attr\./, '');
                            if (name.startsWith('class.')) check(name.slice(6), record.at);
                            else if (name.toLowerCase() === 'class') classExpressionValues(record.expression, record.at, check, () => {});
                        }
                    }
                },
            };
        }
        return {
            Element(element) {
                for (const attribute of [...element.attributes, ...element.inputs]) {
                    const key = attribute.keySpan?.details ?? '';
                    if (attribute.name.toLowerCase() !== 'class' && attribute.name !== 'ngClass' && !key.startsWith('class.')) continue;
                    if (key.startsWith('class.')) check(attribute.name, attribute);
                    else if (attribute.type === 'TextAttribute') check(attribute.value, attribute);
                    else classValues(attribute.value, attribute, check, () => {});
                }
            },
        };
    },
};
