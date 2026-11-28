import { dirname, relative, resolve, sep } from 'node:path';

const app = resolve(import.meta.dirname, '../../../src/main/webapp/app');
const sourceRoot = resolve(app, '../../..');
const basketRoute = 'sharing-basket.route';
const routeImports = new Map([
    ['app.routes', new Set(['sharing/sharing.routes', basketRoute])],
    [basketRoute, new Set(['sharing/sharing.component', 'sharing/sharing-course'])],
]);

// Compare destinations, so aliases and relative paths enforce the same boundary.
function appPath(filename) {
    return relative(app, filename)
        .split(sep)
        .join('/')
        .replace(/\.[cm]?[jt]s$/, '');
}

function importPath(specifier, filename) {
    if (specifier.startsWith('app/')) return appPath(resolve(app, specifier.slice(4)));
    if (specifier.startsWith('src/')) return appPath(resolve(sourceRoot, specifier.slice(4)));
    if (specifier.startsWith('.') || specifier.startsWith('/')) return appPath(resolve(dirname(filename), specifier));
    return undefined;
}

export default {
    meta: {
        type: 'problem',
        schema: [],
        messages: {
            endpoint: 'Sharing owns requests to its CodeAbility endpoints. Use the optional action supplied by the application routes.',
            incoming: 'Only the application routes may wire the optional sharing feature. Consume a programming API extension point instead.',
            outgoing: 'Sharing may import its own code, the public programming API, and shared application infrastructure.',
        },
    },
    create(context) {
        const source = appPath(context.filename);
        const inSharing = source.startsWith('sharing/');
        function check(node) {
            const specifier = node?.type === 'TemplateLiteral' && node.expressions.length === 0 ? node.quasis[0].value.cooked : node?.value;
            if (typeof specifier !== 'string') return;
            const target = importPath(specifier, context.filename);
            if (target === undefined) return;
            if (inSharing) {
                if (
                    target.startsWith('sharing/') ||
                    target.startsWith('programming/api/') ||
                    target.startsWith('foundation/') ||
                    target === 'app.constants' ||
                    target === 'core/layouts/profiles/shared/profile.service'
                ) {
                    return;
                }
                context.report({ node, messageId: 'outgoing' });
            } else if ((target === 'sharing' || target.startsWith('sharing/') || target === basketRoute) && !routeImports.get(source)?.has(target)) {
                context.report({ node, messageId: 'incoming' });
            }
        }
        function checkEndpoint(node, value) {
            if (!inSharing && typeof value === 'string' && /^\/?api\/(?:core|programming)\/sharing(?:[/?#]|$)/.test(value)) {
                context.report({ node, messageId: 'endpoint' });
            }
        }
        return {
            Literal: (node) => checkEndpoint(node, node.value),
            TemplateElement: (node) => checkEndpoint(node, node.value.cooked),
            'ImportDeclaration, ExportAllDeclaration, ExportNamedDeclaration, ImportExpression, TSImportType': (node) => check(node.source),
            TSExternalModuleReference: (node) => check(node.expression),
            'CallExpression[callee.name="require"]': (node) => check(node.arguments[0]),
        };
    },
};
