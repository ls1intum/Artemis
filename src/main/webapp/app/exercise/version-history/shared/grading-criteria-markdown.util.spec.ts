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
});
