import { generateExerciseHintExplanation, parseExerciseHintExplanation, sanitizeStringForMarkdownEditor } from './markdown.util';
import { ExerciseHintExplanationInterface } from 'app/quiz/shared/entities/quiz-question.model';

describe('markdown.util', () => {
    describe('sanitizeStringForMarkdownEditor', () => {
        it('should return undefined for undefined input', () => {
            expect(sanitizeStringForMarkdownEditor(undefined)).toBeUndefined();
        });

        it('should return empty string for empty input', () => {
            expect(sanitizeStringForMarkdownEditor('')).toBe('');
        });

        it('should remove parentheses', () => {
            expect(sanitizeStringForMarkdownEditor('test(value)')).toBe('testvalue');
        });

        it('should remove square brackets', () => {
            expect(sanitizeStringForMarkdownEditor('test[value]')).toBe('testvalue');
        });

        it('should remove all markdown syntax symbols', () => {
            expect(sanitizeStringForMarkdownEditor('a(b)c[d]e')).toBe('abcde');
        });

        it('should remove multiple occurrences of the same symbol', () => {
            expect(sanitizeStringForMarkdownEditor('(a)(b)(c)')).toBe('abc');
        });

        it('should remove nested brackets', () => {
            expect(sanitizeStringForMarkdownEditor('((nested))')).toBe('nested');
        });

        it('should handle strings with only symbols', () => {
            expect(sanitizeStringForMarkdownEditor('()[]')).toBe('');
        });

        it('should preserve other characters', () => {
            expect(sanitizeStringForMarkdownEditor('hello world! 123')).toBe('hello world! 123');
        });

        it('should preserve special characters that are not markdown syntax', () => {
            expect(sanitizeStringForMarkdownEditor('test@example.com')).toBe('test@example.com');
        });

        it('should handle strings with mixed content', () => {
            expect(sanitizeStringForMarkdownEditor('[link](url) normal text (note)')).toBe('linkurl normal text note');
        });

        it('should handle markdown link syntax', () => {
            expect(sanitizeStringForMarkdownEditor('[Click here](https://example.com)')).toBe('Click herehttps://example.com');
        });

        it('should handle markdown image syntax', () => {
            expect(sanitizeStringForMarkdownEditor('![alt](image.png)')).toBe('!altimage.png');
        });

        it('should preserve newlines', () => {
            expect(sanitizeStringForMarkdownEditor('line1\nline2')).toBe('line1\nline2');
        });

        it('should preserve tabs', () => {
            expect(sanitizeStringForMarkdownEditor('col1\tcol2')).toBe('col1\tcol2');
        });
    });

    describe('parseExerciseHintExplanation', () => {
        let targetObject: ExerciseHintExplanationInterface;

        beforeEach(() => {
            targetObject = {
                text: undefined,
                hint: undefined,
                explanation: undefined,
            };
        });

        describe('invalid inputs', () => {
            it('should do nothing for empty markdown text', () => {
                parseExerciseHintExplanation('', targetObject);
                expect(targetObject.text).toBeUndefined();
                expect(targetObject.hint).toBeUndefined();
                expect(targetObject.explanation).toBeUndefined();
            });

            it('should do nothing for null markdown text', () => {
                parseExerciseHintExplanation(null as unknown as string, targetObject);
                expect(targetObject.text).toBeUndefined();
            });

            it('should do nothing for undefined target object', () => {
                // Should not throw
                expect(() => parseExerciseHintExplanation('text', undefined as unknown as ExerciseHintExplanationInterface)).not.toThrow();
            });

            it('should do nothing for null target object', () => {
                expect(() => parseExerciseHintExplanation('text', null as unknown as ExerciseHintExplanationInterface)).not.toThrow();
            });
        });

        describe('text only', () => {
            it('should parse text without hint or explanation', () => {
                parseExerciseHintExplanation('Simple question text', targetObject);
                expect(targetObject.text).toBe('Simple question text');
                expect(targetObject.hint).toBeUndefined();
                expect(targetObject.explanation).toBeUndefined();
            });

            it('should trim whitespace from text', () => {
                parseExerciseHintExplanation('  Text with spaces  ', targetObject);
                expect(targetObject.text).toBe('Text with spaces');
            });

            it('should handle multiline text', () => {
                parseExerciseHintExplanation('Line 1\nLine 2', targetObject);
                expect(targetObject.text).toBe('Line 1\nLine 2');
            });
        });

        describe('text with hint only', () => {
            it('should parse text with hint', () => {
                parseExerciseHintExplanation('Question text\n\t[hint] This is a hint', targetObject);
                expect(targetObject.text).toBe('Question text');
                expect(targetObject.hint).toBe('This is a hint');
                expect(targetObject.explanation).toBeUndefined();
            });

            it('should trim whitespace from hint', () => {
                parseExerciseHintExplanation('Question[hint]   Hint text   ', targetObject);
                expect(targetObject.hint).toBe('Hint text');
            });

            it('should handle hint without preceding text', () => {
                parseExerciseHintExplanation('[hint] Just a hint', targetObject);
                expect(targetObject.text).toBe('');
                expect(targetObject.hint).toBe('Just a hint');
            });
        });

        describe('text with explanation only', () => {
            it('should parse text with explanation', () => {
                parseExerciseHintExplanation('Question text\n\t[exp] This is an explanation', targetObject);
                expect(targetObject.text).toBe('Question text');
                expect(targetObject.hint).toBeUndefined();
                expect(targetObject.explanation).toBe('This is an explanation');
            });

            it('should trim whitespace from explanation', () => {
                parseExerciseHintExplanation('Question[exp]   Explanation text   ', targetObject);
                expect(targetObject.explanation).toBe('Explanation text');
            });

            it('should handle explanation without preceding text', () => {
                parseExerciseHintExplanation('[exp] Just an explanation', targetObject);
                expect(targetObject.text).toBe('');
                expect(targetObject.explanation).toBe('Just an explanation');
            });
        });

        describe('text with both hint and explanation', () => {
            it('should parse text with hint before explanation', () => {
                parseExerciseHintExplanation('Question text\n\t[hint] The hint\n\t[exp] The explanation', targetObject);
                expect(targetObject.text).toBe('Question text');
                expect(targetObject.hint).toBe('The hint');
                expect(targetObject.explanation).toBe('The explanation');
            });

            it('should parse text with explanation before hint', () => {
                parseExerciseHintExplanation('Question text\n\t[exp] The explanation\n\t[hint] The hint', targetObject);
                expect(targetObject.text).toBe('Question text');
                expect(targetObject.hint).toBe('The hint');
                expect(targetObject.explanation).toBe('The explanation');
            });

            it('should handle inline format with hint first', () => {
                parseExerciseHintExplanation('Q[hint]H[exp]E', targetObject);
                expect(targetObject.text).toBe('Q');
                expect(targetObject.hint).toBe('H');
                expect(targetObject.explanation).toBe('E');
            });

            it('should handle inline format with explanation first', () => {
                parseExerciseHintExplanation('Q[exp]E[hint]H', targetObject);
                expect(targetObject.text).toBe('Q');
                expect(targetObject.hint).toBe('H');
                expect(targetObject.explanation).toBe('E');
            });
        });

        describe('complex scenarios', () => {
            it('should handle markdown content in text', () => {
                parseExerciseHintExplanation('**Bold** and *italic* text\n\t[hint] Hint', targetObject);
                expect(targetObject.text).toBe('**Bold** and *italic* text');
                expect(targetObject.hint).toBe('Hint');
            });

            it('should handle code blocks in text', () => {
                parseExerciseHintExplanation('```java\ncode\n```\n\t[exp] Explanation', targetObject);
                expect(targetObject.text).toBe('```java\ncode\n```');
                expect(targetObject.explanation).toBe('Explanation');
            });

            it('should handle LaTeX formulas', () => {
                parseExerciseHintExplanation('Calculate $$x^2 + y^2$$\n\t[hint] Use Pythagorean theorem', targetObject);
                expect(targetObject.text).toBe('Calculate $$x^2 + y^2$$');
                expect(targetObject.hint).toBe('Use Pythagorean theorem');
            });

            it('should handle special characters in hint and explanation', () => {
                parseExerciseHintExplanation('Question\n\t[hint] Use < and > operators\n\t[exp] Compare a < b', targetObject);
                expect(targetObject.hint).toBe('Use < and > operators');
                expect(targetObject.explanation).toBe('Compare a < b');
            });

            it('should handle empty hint', () => {
                parseExerciseHintExplanation('Question\n\t[hint] \n\t[exp] Explanation', targetObject);
                expect(targetObject.hint).toBe('');
                expect(targetObject.explanation).toBe('Explanation');
            });

            it('should handle empty explanation', () => {
                parseExerciseHintExplanation('Question\n\t[hint] Hint\n\t[exp] ', targetObject);
                expect(targetObject.hint).toBe('Hint');
                expect(targetObject.explanation).toBe('');
            });
        });

        describe('artemis markdown format examples', () => {
            it('should parse quiz multiple choice question format', () => {
                parseExerciseHintExplanation('What is 2 + 2?\n\t[hint] Basic arithmetic\n\t[exp] 2 + 2 = 4', targetObject);
                expect(targetObject.text).toBe('What is 2 + 2?');
                expect(targetObject.hint).toBe('Basic arithmetic');
                expect(targetObject.explanation).toBe('2 + 2 = 4');
            });

            it('should parse answer option format', () => {
                parseExerciseHintExplanation('4\n\t[exp] This is the correct answer', targetObject);
                expect(targetObject.text).toBe('4');
                expect(targetObject.explanation).toBe('This is the correct answer');
            });
        });
    });

    describe('generateExerciseHintExplanation', () => {
        describe('empty source object', () => {
            it('should return empty string for undefined text', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: undefined,
                    hint: undefined,
                    explanation: undefined,
                };
                expect(generateExerciseHintExplanation(source)).toBe('');
            });

            it('should return empty string for null text', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: null as unknown as string,
                    hint: undefined,
                    explanation: undefined,
                };
                expect(generateExerciseHintExplanation(source)).toBe('');
            });

            it('should return empty string for empty text', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: '',
                    hint: 'hint',
                    explanation: 'exp',
                };
                expect(generateExerciseHintExplanation(source)).toBe('');
            });
        });

        describe('text only', () => {
            it('should return just text when no hint or explanation', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question text',
                    hint: undefined,
                    explanation: undefined,
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question text');
            });

            it('should return just text when hint and explanation are null', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question text',
                    hint: null as unknown as string,
                    explanation: null as unknown as string,
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question text');
            });

            it('should return just text when hint and explanation are empty strings', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question text',
                    hint: '',
                    explanation: '',
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question text');
            });
        });

        describe('text with hint', () => {
            it('should generate text with hint', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question',
                    hint: 'Helpful hint',
                    explanation: undefined,
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question\n\t[hint] Helpful hint');
            });

            it('should include hint even when explanation is empty string', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question',
                    hint: 'Hint',
                    explanation: '',
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question\n\t[hint] Hint');
            });
        });

        describe('text with explanation', () => {
            it('should generate text with explanation', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question',
                    hint: undefined,
                    explanation: 'Detailed explanation',
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question\n\t[exp] Detailed explanation');
            });

            it('should include explanation even when hint is empty string', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question',
                    hint: '',
                    explanation: 'Explanation',
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question\n\t[exp] Explanation');
            });
        });

        describe('text with both hint and explanation', () => {
            it('should generate text with both hint and explanation', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Question',
                    hint: 'Hint text',
                    explanation: 'Explanation text',
                };
                expect(generateExerciseHintExplanation(source)).toBe('Question\n\t[hint] Hint text\n\t[exp] Explanation text');
            });

            it('should always place hint before explanation', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Q',
                    hint: 'H',
                    explanation: 'E',
                };
                const result = generateExerciseHintExplanation(source);
                expect(result.indexOf('[hint]')).toBeLessThan(result.indexOf('[exp]'));
            });
        });

        describe('complex content', () => {
            it('should handle markdown content in text', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: '**Bold** and `code`',
                    hint: 'Use *emphasis*',
                    explanation: undefined,
                };
                expect(generateExerciseHintExplanation(source)).toBe('**Bold** and `code`\n\t[hint] Use *emphasis*');
            });

            it('should handle multiline text', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Line 1\nLine 2',
                    hint: 'Hint',
                    explanation: undefined,
                };
                expect(generateExerciseHintExplanation(source)).toBe('Line 1\nLine 2\n\t[hint] Hint');
            });

            it('should handle special characters', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'What is a < b?',
                    hint: 'Compare using <',
                    explanation: 'a < b means a is less than b',
                };
                const result = generateExerciseHintExplanation(source);
                expect(result).toContain('What is a < b?');
                expect(result).toContain('Compare using <');
                expect(result).toContain('a < b means a is less than b');
            });

            it('should handle LaTeX formulas', () => {
                const source: ExerciseHintExplanationInterface = {
                    text: 'Calculate $$x^2$$',
                    hint: 'Use $$a^n$$',
                    explanation: '$$x^2 = x * x$$',
                };
                const result = generateExerciseHintExplanation(source);
                expect(result).toContain('$$x^2$$');
                expect(result).toContain('[hint] Use $$a^n$$');
                expect(result).toContain('[exp] $$x^2 = x * x$$');
            });
        });

        describe('roundtrip with parseExerciseHintExplanation', () => {
            it('should produce parseable output for text only', () => {
                const original: ExerciseHintExplanationInterface = { text: 'Question', hint: undefined, explanation: undefined };
                const generated = generateExerciseHintExplanation(original);
                const parsed: ExerciseHintExplanationInterface = { text: undefined, hint: undefined, explanation: undefined };
                parseExerciseHintExplanation(generated, parsed);
                expect(parsed.text).toBe(original.text);
            });

            it('should produce parseable output for text with hint', () => {
                const original: ExerciseHintExplanationInterface = { text: 'Question', hint: 'Hint', explanation: undefined };
                const generated = generateExerciseHintExplanation(original);
                const parsed: ExerciseHintExplanationInterface = { text: undefined, hint: undefined, explanation: undefined };
                parseExerciseHintExplanation(generated, parsed);
                expect(parsed.text).toBe(original.text);
                expect(parsed.hint).toBe(original.hint);
            });

            it('should produce parseable output for text with explanation', () => {
                const original: ExerciseHintExplanationInterface = { text: 'Question', hint: undefined, explanation: 'Explanation' };
                const generated = generateExerciseHintExplanation(original);
                const parsed: ExerciseHintExplanationInterface = { text: undefined, hint: undefined, explanation: undefined };
                parseExerciseHintExplanation(generated, parsed);
                expect(parsed.text).toBe(original.text);
                expect(parsed.explanation).toBe(original.explanation);
            });

            it('should produce parseable output for text with both hint and explanation', () => {
                const original: ExerciseHintExplanationInterface = { text: 'Question', hint: 'Hint', explanation: 'Explanation' };
                const generated = generateExerciseHintExplanation(original);
                const parsed: ExerciseHintExplanationInterface = { text: undefined, hint: undefined, explanation: undefined };
                parseExerciseHintExplanation(generated, parsed);
                expect(parsed.text).toBe(original.text);
                expect(parsed.hint).toBe(original.hint);
                expect(parsed.explanation).toBe(original.explanation);
            });

            it('should handle complex content roundtrip', () => {
                const original: ExerciseHintExplanationInterface = {
                    text: 'What is $$a^2 + b^2 = c^2$$?',
                    hint: 'Think of **Pythagoras**',
                    explanation: 'The Pythagorean theorem states that `a² + b² = c²`',
                };
                const generated = generateExerciseHintExplanation(original);
                const parsed: ExerciseHintExplanationInterface = { text: undefined, hint: undefined, explanation: undefined };
                parseExerciseHintExplanation(generated, parsed);
                expect(parsed.text).toBe(original.text);
                expect(parsed.hint).toBe(original.hint);
                expect(parsed.explanation).toBe(original.explanation);
            });
        });
    });
});
