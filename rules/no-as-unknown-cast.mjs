import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * Forbids `as unknown` type assertions in client PRODUCTION code under `src/main/webapp`.
 *
 * `x as unknown` — and especially the `x as unknown as T` double-cast — completely disables the type
 * checker: `unknown` is assignable from anything, and asserting away from `unknown` is allowed to any
 * type, so the compiler can no longer catch a mismatch between the real runtime shape and `T`. These
 * casts are a recurring source of runtime errors that strict TypeScript would otherwise prevent.
 *
 * The correct fix is almost always to address the *underlying* type rather than paper over it: correct a
 * wrong type annotation, fix the DTO / return type / generic at the source, add a type guard, or
 * restructure the data flow. Widening a value to `unknown` never needs a cast — assignment to an
 * `unknown`-typed variable/parameter is always allowed.
 *
 * The cast is tolerated only in test code (co-located `*.spec.ts` files), where casting to build mocks
 * and fixtures is sometimes pragmatic and the blast radius is limited to the test.
 */
export default createRule({
    name: 'no-as-unknown-cast',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Forbid `as unknown` casts (including the `as unknown as T` double-cast) in client production code, because they bypass type safety and risk runtime errors. Allowed only in *.spec.ts test files.',
        },
        messages: {
            noAsUnknown:
                '`as unknown` casts bypass the type checker and risk runtime errors. Fix the underlying type (DTO, return type, generic), add a type guard, or narrow the value instead of casting. (Only allowed in *.spec.ts test code.)',
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

        // Reports both `x as unknown` (TSAsExpression) — which is also the inner node of the
        // `x as unknown as T` double-cast — and the legacy angle-bracket form `<unknown>x` (TSTypeAssertion).
        const reportWhenAssertingToUnknown = (node) => {
            if (node.typeAnnotation?.type === 'TSUnknownKeyword') {
                context.report({ node, messageId: 'noAsUnknown' });
            }
        };

        return {
            TSAsExpression: reportWhenAssertingToUnknown,
            TSTypeAssertion: reportWhenAssertingToUnknown,
        };
    },
});
