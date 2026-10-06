import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * Disallows Router navigation (`navigate` / `navigateByUrl`) inside an `effect()` callback unless it is wrapped in
 * `untracked(...)`.
 *
 * Navigating from an effect is a global, imperative side effect: it destroys and re-creates components and rewrites
 * their signals, which feeds back into the effect's own dependencies and easily produces an infinite re-navigation
 * loop. This is exactly what crashed the online code editor (PR #12976): an incoming result replaced a participation
 * object, re-running the navigation effect, which navigated again, re-creating the whole subtree, and so on — flooding
 * the server with thousands of requests until the browser tab ran out of memory.
 *
 * Prefer driving navigation from explicit user actions, route guards/resolvers, or `ngOnChanges`. If reactive
 * navigation is genuinely required, depend on a *stable* `computed()` key (so object-reference churn does not re-run
 * the effect) and wrap the `router.navigate(...)` call in `untracked(...)`. Wrapping in `untracked` satisfies this rule.
 *
 * Note: the check is lexical — navigation hidden in a helper method called from the effect is not detected. The
 * companion client guideline (client-development.mdx) also discourages writing signals in effects, which cannot be
 * linted reliably because `.set()` / `.update()` are indistinguishable from `Map`/`Set` mutations at the syntax level.
 */
const rule = createRule({
    name: 'no-navigation-in-effect',
    meta: {
        type: 'problem',
        docs: { description: 'Disallow Router navigation inside effect() unless wrapped in untracked()' },
        messages: {
            navigateInEffect:
                'Avoid calling Router.{{method}}() inside effect(): navigation re-creates components and rewrites signals, which can re-trigger the effect and cause an infinite re-navigation loop (see PR #12976). Drive navigation from user actions / route guards / ngOnChanges, or — if reactive navigation is unavoidable — depend on a stable computed() key and wrap the navigation in untracked(...).',
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        return {
            "CallExpression[callee.type='MemberExpression'][callee.property.name=/^(navigate|navigateByUrl)$/]"(node) {
                let current = node.parent;
                let insideUntracked = false;
                while (current) {
                    if (current.type === 'CallExpression' && current.callee && current.callee.type === 'Identifier') {
                        if (current.callee.name === 'untracked') {
                            insideUntracked = true;
                        } else if (current.callee.name === 'effect') {
                            if (!insideUntracked) {
                                context.report({ node, messageId: 'navigateInEffect', data: { method: node.callee.property.name } });
                            }
                            return;
                        }
                    }
                    current = current.parent;
                }
            },
        };
    },
});

export default rule;
