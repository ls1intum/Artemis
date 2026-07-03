import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * Forbids `as any` type assertions in client PRODUCTION code under `src/main/webapp`.
 *
 * `x as any` opts the expression out of type checking entirely: `any` is assignable both to and from every
 * type, so the compiler can no longer catch a mismatch between the real runtime shape and how the value is
 * used. These casts are a recurring source of runtime errors that strict TypeScript would otherwise prevent.
 *
 * The correct fix is almost always to address the *underlying* type rather than paper over it: correct a
 * wrong type annotation, fix the DTO / return type / generic at the source, add a type guard, augment a
 * third-party or DOM type, or narrow the value. When a value is genuinely dynamic, prefer `unknown` and
 * narrow it before use.
 *
 * The cast is tolerated only in test code (co-located `*.spec.ts` files), where casting to build mocks
 * and fixtures is sometimes pragmatic and the blast radius is limited to the test.
 */
export default createRule({
    name: 'no-as-any-cast',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Forbid `as any` casts (including the angle-bracket `<any>x` form) in client production code, because they bypass type safety and risk runtime errors. Allowed only in *.spec.ts test files.',
        },
        messages: {
            noAsAny:
                '`as any` casts bypass the type checker and risk runtime errors. Fix the underlying type (DTO, return type, generic), add a type guard, augment the DOM/third-party type, or narrow via `unknown` instead of casting. (Only allowed in *.spec.ts test code.)',
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

        // Reports both `x as any` (TSAsExpression) and the legacy angle-bracket form `<any>x` (TSTypeAssertion).
        const reportWhenAssertingToAny = (node) => {
            if (node.typeAnnotation?.type === 'TSAnyKeyword') {
                context.report({ node, messageId: 'noAsAny' });
            }
        };

        return {
            TSAsExpression: reportWhenAssertingToAny,
            TSTypeAssertion: reportWhenAssertingToAny,
        };
    },
});
