import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * @fileoverview Requires `deepClone` / `cloneWith` / `hydrate` instead of object spread, `Object.assign`
 * and `structuredClone` in client PRODUCTION code under `src/main/webapp`.
 *
 * ## Why
 *
 * All three alternatives copy an object incorrectly, in ways the type checker cannot see:
 *
 * - `structuredClone()` drops prototypes. A cloned `dayjs` date loses every method, so `date.format(...)`
 *   throws — and `dayjs.isDayjs(clone)` still returns `true`, because dayjs marks instances with an own
 *   enumerable `$isDayjsObject` property that survives the clone. No guard can catch the corruption. This
 *   is the bug that motivated `deepClone` (PR #11910, `LectureTitleChannelNameComponent`).
 * - Object spread (`{ ...obj }`) and `Object.assign({}, obj)` copy exactly one level. Nested objects and
 *   arrays stay shared with the original, so editing the "copy" silently edits the original too. Artemis
 *   entities (`Course`, `Exercise`, `Exam`, `Lecture`, …) are deep graphs, so this is a live hazard.
 * - `Object.assign(target, source)` additionally mutates `target` in place. Under zoneless change
 *   detection a signal compares with `Object.is`, so mutating and re-setting the same reference emits no
 *   notification and nothing re-renders.
 *
 * ## What to use instead
 *
 * All three live in `app/foundation/util/deep-clone.util`:
 *
 * - `deepClone(x)` — replaces `{ ...x }` and `Object.assign({}, x)`
 * - `cloneWith(x, { a, b })` — replaces `{ ...x, a, b }` and `Object.assign({}, x, { a, b })`
 * - `hydrate(new Course(), dto)` — replaces `Object.assign(new Course(), dto)`, the DTO-to-model idiom
 *   whose point is to give parsed JSON a prototype rather than to copy an object
 *
 * ## Scope
 *
 * Production client TypeScript only. Co-located `*.spec.ts` files are exempt: building a fixture by
 * spreading a literal is pragmatic and the blast radius is the test.
 *
 * ## Not flagged
 *
 * - **Array spread** — `[...items, newItem]` is the documented way to append immutably.
 * - **Call spread** — `fn(...args)`, `Math.max(...values)`.
 * - **Object rest in destructuring** — `const { id, ...rest } = post` reads properties, it does not copy
 *   an object into a new one. It is a `RestElement` in an `ObjectPattern`, a different node than the
 *   `SpreadElement` in an `ObjectExpression` that this rule reports.
 *
 * ## If you only need a signal to emit
 *
 * Some call sites do not want a copy at all: an object was mutated in place and a signal has to emit so the view
 * updates. Copying it to change its identity detaches the nested associations, which re-creates `track`-by-identity
 * rows and re-notifies child inputs on every change-detection pass (ending in NG0103). Declare the signal with
 * `equal: () => false` and re-set the same reference instead — see `CourseUpdateComponent.commitCourse`. Where the
 * state is not signal-backed, build the replacement object explicitly, field by field, as
 * `MetisService.rebuildPostReference` does.
 *
 * Full rationale and examples:
 * documentation/docs/developer/guidelines/client-development.mdx ("Cloning objects").
 */
export default createRule({
    name: 'prefer-deep-clone',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Require deepClone / cloneWith / hydrate from app/foundation/util/deep-clone.util instead of object spread, Object.assign and structuredClone, which copy one level deep or drop prototypes and silently corrupt dayjs dates and nested entity graphs. Allowed only in *.spec.ts test files.',
        },
        messages: {
            objectSpread:
                'Object spread copies only one level, so nested objects and arrays stay shared with the original and later edits mutate both. Use `deepClone(x)` from `app/foundation/util/deep-clone.util` for a plain copy, or `cloneWith(x, { … })` to copy and override in one expression. Array spread (`[...items, x]`) and object rest (`const { a, ...rest } = x`) are unaffected. See documentation/docs/developer/guidelines/client-development.mdx (### Cloning objects).',
            objectAssign:
                '`Object.assign` copies only one level, and with a non-empty target it mutates that target in place — which emits no signal notification, because a signal compares with `Object.is`. Use `deepClone(x)` to copy, `cloneWith(x, { … })` to copy and override, or `hydrate(new X(), dto)` to turn a DTO into a typed model instance. All three live in `app/foundation/util/deep-clone.util`. See documentation/docs/developer/guidelines/client-development.mdx (### Cloning objects).',
            structuredCloneCall:
                '`structuredClone` does not preserve prototypes, so a cloned `dayjs` date loses every method and `date.format(...)` throws — while `dayjs.isDayjs()` still returns true, so no guard catches it. Use `deepClone(x)` from `app/foundation/util/deep-clone.util`. See documentation/docs/developer/guidelines/client-development.mdx (### Cloning objects).',
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        // Normalize Windows backslashes so the client-code path check works across operating systems.
        const filename = (context.filename ?? context.getFilename()).replaceAll('\\', '/');

        // Apply to client production code only. Skip non-client files and co-located test specs.
        if (!filename.includes('src/main/webapp/')) {
            return {};
        }
        if (filename.endsWith('.spec.ts')) {
            return {};
        }

        /** True for `Object.assign`, written either bare or as a member of an aliased `Object`. */
        const isObjectAssign = (callee) =>
            callee.type === 'MemberExpression' &&
            !callee.computed &&
            callee.object.type === 'Identifier' &&
            callee.object.name === 'Object' &&
            callee.property.type === 'Identifier' &&
            callee.property.name === 'assign';

        /** True for `structuredClone(...)` and `window.structuredClone(...)`. */
        const isStructuredClone = (callee) => {
            if (callee.type === 'Identifier') {
                return callee.name === 'structuredClone';
            }
            return (
                callee.type === 'MemberExpression' &&
                !callee.computed &&
                callee.property.type === 'Identifier' &&
                callee.property.name === 'structuredClone'
            );
        };

        return {
            // Only spreads inside an object literal. A SpreadElement in an ArrayExpression (array spread) or in
            // a CallExpression (call spread) has the same node type but a different parent, and both are allowed.
            SpreadElement(node) {
                if (node.parent?.type === 'ObjectExpression') {
                    context.report({ node, messageId: 'objectSpread' });
                }
            },
            CallExpression(node) {
                if (isObjectAssign(node.callee)) {
                    context.report({ node, messageId: 'objectAssign' });
                } else if (isStructuredClone(node.callee)) {
                    context.report({ node, messageId: 'structuredCloneCall' });
                }
            },
        };
    },
});
