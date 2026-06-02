import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * Modules that have been fully migrated to Angular signal-based APIs.
 * Legacy decorators (@Input, @Output, @ViewChild, @ViewChildren, @ContentChild, @ContentChildren)
 * must not be reintroduced in these modules.
 */
// TODO: Add other modules here once they have been fully migrated to Angular signal-based APIs.
// Still pending (legacy decorators remain): exercise.
const MIGRATED_MODULES = [
    'account',
    'admin',
    'assessment',
    'atlas',
    'calendar',
    'communication',
    'core',
    'course',
    'editor',
    'exam',
    'exercise/exercise-headers',
    'exercise/feedback',
    'exercise/result',
    'exercise/result-history',
    'fileupload',
    'foundation',
    'hyperion',
    'iris',
    'lecture',
    'localci',
    'localvc',
    'logos',
    'lti',
    'modeling',
    'notification',
    'plagiarism',
    'programming',
    'quiz',
    'shared-ui',
    'sharing',
    'text',
    'tutorialgroup',
];

const FORBIDDEN_DECORATORS = new Set(['Input', 'Output', 'ViewChild', 'ViewChildren', 'ContentChild', 'ContentChildren']);

const SIGNAL_REPLACEMENTS = {
    Input: 'input() / input.required()',
    Output: 'output()',
    ViewChild: 'viewChild() / viewChild.required()',
    ViewChildren: 'viewChildren()',
    ContentChild: 'contentChild() / contentChild.required()',
    ContentChildren: 'contentChildren()',
};

export default createRule({
    name: 'enforce-signal-apis-in-migrated-modules',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Forbid legacy Angular decorators (@Input, @Output, @ViewChild, @ViewChildren, @ContentChild, @ContentChildren) in modules that have been fully migrated to Angular signal-based APIs.',
        },
        messages: {
            forbiddenDecorator:
                "Legacy decorator '@{{decoratorName}}' is not allowed in the '{{moduleName}}' module which has been migrated to signal-based APIs. Use {{replacement}} instead.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        // Normalize Windows backslashes so the `/app/<module>/` check works across operating systems.
        const filename = (context.filename ?? context.getFilename()).replaceAll('\\', '/');

        // Apply to all files (including test/spec files) in migrated modules
        const migratedModule = MIGRATED_MODULES.find((mod) => filename.includes(`/app/${mod}/`));
        if (!migratedModule) {
            return {};
        }

        return {
            Decorator(node) {
                let decoratorName;
                const expr = node.expression;

                if (expr.type === 'CallExpression' && expr.callee.type === 'Identifier') {
                    decoratorName = expr.callee.name;
                } else if (expr.type === 'Identifier') {
                    decoratorName = expr.name;
                }

                if (decoratorName && FORBIDDEN_DECORATORS.has(decoratorName)) {
                    context.report({
                        node,
                        messageId: 'forbiddenDecorator',
                        data: {
                            decoratorName,
                            moduleName: migratedModule,
                            replacement: SIGNAL_REPLACEMENTS[decoratorName],
                        },
                    });
                }
            },
        };
    },
});
