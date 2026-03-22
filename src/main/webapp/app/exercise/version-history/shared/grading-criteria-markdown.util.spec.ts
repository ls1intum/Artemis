import { describe, expect, it } from 'vitest';
import { serializeGradingCriteriaToMarkdown } from 'app/exercise/version-history/shared/grading-criteria-markdown.util';

describe('serializeGradingCriteriaToMarkdown', () => {
    it('should serialize grading instructions and criteria into canonical markdown', () => {
        const markdown = serializeGradingCriteriaToMarkdown('Top level instruction', [
            {
                id: 1,
                title: 'Correctness',
                structuredGradingInstructions: [
                    {
                        id: 2,
                        credits: 2,
                        gradingScale: 'positive',
                        instructionDescription: 'Good approach',
                        feedback: 'Keep it',
                        usageCount: 3,
                    },
                ],
            },
        ]);

        expect(markdown).toContain('Top level instruction');
        expect(markdown).toContain('[criterion] Correctness');
        expect(markdown).toContain('[instruction]');
        expect(markdown).toContain('[credits] 2');
        expect(markdown).toContain('[feedback] Keep it');
    });

    it('should return undefined when both inputs are empty or missing', () => {
        expect(serializeGradingCriteriaToMarkdown(undefined, undefined)).toBeUndefined();
        expect(serializeGradingCriteriaToMarkdown(undefined, [])).toBeUndefined();
        expect(serializeGradingCriteriaToMarkdown('', [])).toBeUndefined();
        expect(serializeGradingCriteriaToMarkdown('   ', [])).toBeUndefined();
    });

    it('should serialize only top-level instructions when no criteria are provided', () => {
        const markdown = serializeGradingCriteriaToMarkdown('Only instructions', []);
        expect(markdown).toBe('Only instructions');
    });

    it('should serialize criteria without top-level instructions', () => {
        const markdown = serializeGradingCriteriaToMarkdown(undefined, [
            {
                id: 1,
                title: 'Style',
                structuredGradingInstructions: [
                    {
                        id: 2,
                        credits: 1,
                        gradingScale: 'minor',
                        instructionDescription: 'Formatting',
                        feedback: 'Good style',
                        usageCount: 1,
                    },
                ],
            },
        ]);

        expect(markdown).toBeDefined();
        expect(markdown).toContain('[criterion] Style');
        expect(markdown).toContain('[credits] 1');
    });

    it('should skip criteria without title and without instructions', () => {
        const markdown = serializeGradingCriteriaToMarkdown('Instructions', [
            {
                id: 1,
                title: undefined,
                structuredGradingInstructions: [],
            },
        ]);

        expect(markdown).toBe('Instructions');
    });

    it('should handle criteria with title but no instructions', () => {
        const markdown = serializeGradingCriteriaToMarkdown(undefined, [
            {
                id: 1,
                title: 'Empty Criterion',
                structuredGradingInstructions: [],
            },
        ]);

        expect(markdown).toBeDefined();
        expect(markdown).toContain('[criterion] Empty Criterion');
    });
});
