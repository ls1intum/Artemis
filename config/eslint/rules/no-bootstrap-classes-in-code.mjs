import { isBanned } from './no-bootstrap-classes.mjs';

/**
 * Forbid Bootstrap class names in TypeScript. Bootstrap is removed from the client; the rule keeps its class names
 * from coming back in code.
 *
 * `no-bootstrap-classes` reads templates, so a class that a component computes (`'text-' + color`), keeps in a map
 * (`{ danger: 'text-danger' }`), passes to a library (`styleClass: 'btn btn-sm'`) or looks up in the DOM
 * (`querySelector('.modal-dialog')`) is invisible to it. Such a name styles or finds nothing, and the template lint
 * cannot see it.
 *
 * The rule judges string literals and the static chunks of template literals:
 *
 *   - Every token, and every class of a selector, that the template rule bans is reported, EXCEPT a bare word without
 *     a hyphen (`table`, `card`, `row`, `close`, `badge`, `modal`, `h1`, `small`, ...): in code these are ordinary
 *     identifiers and discriminants (`type: 'card'`), and reporting them would drown the real findings. `btn` is the
 *     one bare word that is always a class.
 *   - A literal that ends in a Bootstrap family prefix and is continued by an expression (`'text-' + color`,
 *     `` `btn-${variant}` ``) is reported, because the class it computes is Bootstrap's.
 *
 * Co-located specs are not scanned: they may assert that a class is gone. Scope: eslint.config.mjs, locked folders only.
 */

// Words that are classes of their own even without a hyphen.
const ALWAYS_A_CLASS = new Set(['btn', 'clearfix', 'hstack', 'vstack']);

// A literal that ends like this and is continued dynamically names a Bootstrap class by its prefix. `text-` and `bg-` also
// start Tailwind utilities, but Tailwind generates no class for a computed name, so a value after them names Bootstrap's
// semantic colour (`text-danger`).
const DYNAMIC_PREFIX = /(^|[\s.])(btn|alert|badge|text|table|list-group-item|border|bg|link)-$/;

// Splits a class list, a selector (`.card .card-body > .btn`) or an attribute selector into candidate class names.
const SEPARATORS = /[\s.>+~,:()[\]="'#]+/;

function bannedTokens(value) {
    return value
        .split(SEPARATORS)
        .filter(Boolean)
        .filter((token) => (token.includes('-') || ALWAYS_A_CLASS.has(token)) && isBanned(token));
}

function isContinuedDynamically(node) {
    const parent = node.parent;
    // `'text-' + 'danger'` joins two constants and computes nothing; only a value that is not a literal decides the class.
    return parent?.type === 'BinaryExpression' && parent.operator === '+' && parent.left === node && parent.right.type !== 'Literal';
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid class names of the removed Bootstrap library in TypeScript; the template rules cannot see classes built in code.',
        },
        messages: {
            bootstrapClassInCode:
                "Bootstrap class '{{cls}}' in code has no effect, Bootstrap is removed from the client: use a TUM AET UI input, a Tailwind utility with a semantic token, or the component's own state (`text-state-danger`, `font-bold`, ...).",
            dynamicBootstrapClass:
                "'{{prefix}}' plus an expression builds a Bootstrap class name ('{{prefix}}danger', ...). Map the value to a TUM AET UI variant or a semantic Tailwind class (`text-state-*`) instead.",
        },
        schema: [],
    },
    create(context) {
        function check(node, value, dynamicTail) {
            for (const cls of bannedTokens(value)) {
                context.report({ node, messageId: 'bootstrapClassInCode', data: { cls } });
            }
            if (dynamicTail) {
                const match = DYNAMIC_PREFIX.exec(value);
                if (match) {
                    context.report({ node, messageId: 'dynamicBootstrapClass', data: { prefix: `${match[2]}-` } });
                }
            }
        }
        return {
            Literal(node) {
                if (typeof node.value !== 'string' || node.parent?.type === 'ImportDeclaration' || node.parent?.type === 'ExportAllDeclaration') {
                    return;
                }
                check(node, node.value, isContinuedDynamically(node));
            },
            TemplateLiteral(node) {
                node.quasis.forEach((quasi, index) => {
                    check(quasi, quasi.value.cooked ?? quasi.value.raw, index < node.expressions.length);
                });
            },
        };
    },
};
