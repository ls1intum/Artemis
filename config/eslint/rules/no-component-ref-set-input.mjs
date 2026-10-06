import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * Forbids calling `setInput` on a component reference in client PRODUCTION code.
 *
 * `ComponentRef.setInput(name: string, value: unknown)` identifies the input by a plain string and accepts any value, so
 * the compiler cannot catch a misspelled or removed input or a value of the wrong type. At runtime Angular only logs
 * NG0303 with `console.error` (it does not throw), and nothing at all in production, so the component silently keeps
 * its default and the bug surfaces far from its cause.
 *
 * Preferred, in this order:
 *   1. Declare the component in a template. When the set of components is closed and known, a `@switch` over the
 *      alternatives is type checked by the template compiler, including every input name and value.
 *   2. For a component that has to be created in code, use `setInputs` from `app/foundation/util/set-inputs.util`
 *      (inside `packages/tum-aet-ui` its internal counterpart), which checks the names and value types at compile time.
 *
 * The typed wrappers themselves are the only places that disable this rule. Test code (`*.spec.ts`) is exempt: it sets
 * inputs on fixtures and the blast radius is the test.
 */
export default createRule({
    name: 'no-component-ref-set-input',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Forbid calling `setInput` with a string input name on a component reference in client production code, because the name and the value are not type checked. Allowed only in *.spec.ts test files.',
        },
        messages: {
            noSetInput:
                '`setInput` takes the input name as a plain string, so a misspelled input or a value of the wrong type is not caught by the compiler. Declare the component in a template (for a closed set of components use a `@switch`), or use `setInputs` from `app/foundation/util/set-inputs.util`.',
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        // Normalize Windows backslashes so the path check works across operating systems.
        const filename = (context.filename ?? context.getFilename()).replaceAll('\\', '/');
        if (filename.endsWith('.spec.ts')) {
            return {};
        }

        // The name a member access spells out: `x.name`, `x?.name`, `x['name']` and x[`name`]. Undefined for a dynamic key.
        const staticMemberName = (node) => {
            if (node.type !== 'MemberExpression') {
                return undefined;
            }
            const property = node.property;
            if (!node.computed) {
                return property.type === 'Identifier' ? property.name : undefined;
            }
            if (property.type === 'Literal' && typeof property.value === 'string') {
                return property.value;
            }
            if (property.type === 'TemplateLiteral' && property.expressions.length === 0) {
                return property.quasis[0].value.cooked;
            }
            return undefined;
        };
        const isSetInputMember = (node) => staticMemberName(node) === 'setInput';
        // The member to report: for `x.setInput` the name of `setInput` itself.
        const reportTarget = (member) => member.property;

        return {
            CallExpression(node) {
                const callee = node.callee;
                // `ref.setInput(...)`, `this.ref.setInput(...)`, `this.ref?.setInput(...)` and `ref['setInput'](...)`.
                if (isSetInputMember(callee)) {
                    context.report({ node: reportTarget(callee), messageId: 'noSetInput' });
                    return;
                }
                // `ref.setInput.call(...)`, `.apply(...)` and `.bind(...)` call it as well, also spelled `ref.setInput['call'](...)`.
                if (['call', 'apply', 'bind'].includes(staticMemberName(callee)) && isSetInputMember(callee.object)) {
                    context.report({ node: reportTarget(callee.object), messageId: 'noSetInput' });
                }
            },
        };
    },
});
