import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';

export const Mapping: StaticCodeAnalysisCategory[] = [
    {
        id: 1,
        name: 'Bad Practice',
        description: 'Violations of recommended and essential coding practice. Rules which enforce generally accepted best practices',
        state: StaticCodeAnalysisCategoryState.Graded,
        penalty: 1,
        maxPenalty: 10,
        checks: [
            {
                tool: 'spotbugs',
                check: 'BAD_PRACTICE',
            },
            {
                tool: 'checkstlye',
                check: 'coding',
            },
            {
                tool: 'pmd',
                check: 'best-practices',
            },
        ],
    },
    {
        id: 2,
        name: 'Styling',
        description: 'Confusing Code, Formatting, Import-Order, Modifier-Order, Line-Length, etc.',
        state: StaticCodeAnalysisCategoryState.Feedback,
        penalty: 0,
        maxPenalty: 0,
        checks: [
            {
                tool: 'spotbugs',
                check: 'STYLE',
            },
            {
                tool: 'checkstyle',
                check: 'annotation',
            },
            {
                tool: 'checkstyle',
                check: 'blocks',
            },
            {
                tool: 'checkstyle',
                check: 'imports',
            },
            {
                tool: 'checkstyle',
                check: 'indentation',
            },
            {
                tool: 'checkstyle',
                check: 'metrics',
            },
            {
                tool: 'checkstyle',
                check: 'modifier',
            },
            {
                tool: 'checkstyle',
                check: 'sizes',
            },
            {
                tool: 'checkstyle',
                check: 'whitespaces',
            },
            {
                tool: 'pmd',
                check: 'code-style',
            },
        ],
    },
];
