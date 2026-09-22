import postcss from 'postcss';
import { compiledClasses, TailwindVerificationError } from './design-system/tailwind/client.mjs';
import { plugin as engine } from './design-system/plugin.mjs';
import { evaluateStylesheet } from './angular-design-system-stylelint.mjs';
import { checkPrivateClasses } from './design-system-private-classes-stylelint.mjs';

/** Check actual variant selectors, not a second implementation of Tailwind's selector grammar. */
export function createClassStylesRule(adapterFor, getIndex, theme) {
    return {
        meta: {
            type: 'problem',
            schema: [
                {
                    type: 'object',
                    properties: {
                        propertyOptions: engine.rules['no-inline-styles'].meta.schema[0],
                        privateClassPrefix: { type: 'string', minLength: 1 },
                    },
                    additionalProperties: false,
                },
            ],
            messages: {
                restyle: 'Class "{{token}}" generates a selector that violates the design-system boundary. {{message}}',
                compiler: 'Cannot verify generated class selectors: {{reason}}. Fix the configured Tailwind theme/imports; do not disable this check.',
            },
        },
        create(context) {
            const { propertyOptions = {}, privateClassPrefix } = context.options[0] ?? {};
            const sites = new Map();
            const visitors = adapterFor(context).classSiteVisitors(context, { includeUnprotected: true }, (site) => {
                for (const { value, node } of site.contextualStrings) {
                    for (const token of value.split(/\s+/).filter((token) => token.includes(':'))) {
                        if (!sites.has(token)) sites.set(token, new Set());
                        sites.get(token).add(node);
                    }
                }
            });
            return {
                ...visitors,
                'Program:exit'() {
                    if (!sites.size) return;
                    const tokens = [...sites.keys()];
                    let styles;
                    try {
                        styles = compiledClasses(theme, tokens);
                    } catch (error) {
                        if (!(error instanceof TailwindVerificationError)) throw error;
                        context.report({ node: sites.get(tokens[0]).values().next().value, messageId: 'compiler', data: { reason: error.message } });
                        return;
                    }
                    for (const [index, css] of styles.entries()) {
                        if (!css) continue; // Unknown classes are diagnosed by the vocabulary rule on protected hosts.
                        const root = postcss.parse(css);
                        const messages = new Set();
                        const report = ({ message }) => messages.add(message);
                        if (privateClassPrefix) checkPrivateClasses(root, privateClassPrefix, report);
                        if (!messages.size) evaluateStylesheet(root, getIndex(), propertyOptions, report);
                        for (const message of messages)
                            for (const node of sites.get(tokens[index])) context.report({ node, messageId: 'restyle', data: { token: tokens[index], message } });
                    }
                },
            };
        },
    };
}
