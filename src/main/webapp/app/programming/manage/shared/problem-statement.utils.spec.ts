import {
    InlineRefinementEvent,
    buildGenerationRequest,
    buildGlobalRefinementRequest,
    buildTargetedRefinementRequest,
    getCourseId,
    isTemplateOrEmpty,
    isValidGenerationResponse,
    isValidRefinementResponse,
    normalizeString,
} from './problem-statement.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

describe('ProblemStatementUtils', () => {
    describe('normalizeString', () => {
        it('should return empty string for undefined', () => {
            expect(normalizeString(undefined)).toBe('');
        });

        it('should return empty string for empty string', () => {
            expect(normalizeString('')).toBe('');
        });

        it('should trim whitespace', () => {
            expect(normalizeString('  hello  ')).toBe('hello');
        });

        it('should normalize Windows line endings', () => {
            expect(normalizeString('line1\r\nline2')).toBe('line1\nline2');
        });

        it('should normalize old Mac carriage returns', () => {
            expect(normalizeString('line1\rline2')).toBe('line1\nline2');
        });

        it('should handle mixed line endings', () => {
            expect(normalizeString('a\r\nb\rc\nd')).toBe('a\nb\nc\nd');
        });
    });

    describe('getCourseId', () => {
        it('should return undefined for undefined exercise', () => {
            expect(getCourseId(undefined)).toBeUndefined();
        });

        it('should return course id from direct course', () => {
            const exercise = { course: { id: 42 } as Course } as ProgrammingExercise;
            expect(getCourseId(exercise)).toBe(42);
        });

        it('should return course id from exam exercise group', () => {
            const exercise = {
                exerciseGroup: {
                    exam: {
                        course: { id: 99 } as Course,
                    } as Exam,
                } as ExerciseGroup,
            } as ProgrammingExercise;
            expect(getCourseId(exercise)).toBe(99);
        });

        it('should prefer direct course over exam course', () => {
            const exercise = {
                course: { id: 1 } as Course,
                exerciseGroup: {
                    exam: {
                        course: { id: 2 } as Course,
                    } as Exam,
                } as ExerciseGroup,
            } as ProgrammingExercise;
            expect(getCourseId(exercise)).toBe(1);
        });

        it('should return undefined when no course is available', () => {
            const exercise = {} as ProgrammingExercise;
            expect(getCourseId(exercise)).toBeUndefined();
        });
    });

    describe('isTemplateOrEmpty', () => {
        it('should return true for undefined problem statement', () => {
            expect(isTemplateOrEmpty(undefined, 'template', true)).toBeTruthy();
        });

        it('should return true for empty problem statement', () => {
            expect(isTemplateOrEmpty('', 'template', true)).toBeTruthy();
        });

        it('should return true for whitespace-only problem statement', () => {
            expect(isTemplateOrEmpty('   ', 'template', true)).toBeTruthy();
        });

        it('should return false when template is not loaded', () => {
            expect(isTemplateOrEmpty('some content', 'template', false)).toBeFalsy();
        });

        it('should return true when problem statement matches template', () => {
            expect(isTemplateOrEmpty('my template', 'my template', true)).toBeTruthy();
        });

        it('should return true when normalized problem statement matches normalized template', () => {
            expect(isTemplateOrEmpty('  my template\r\n', '  my template\n', true)).toBeTruthy();
        });

        it('should return false when problem statement differs from template', () => {
            expect(isTemplateOrEmpty('custom content', 'template', true)).toBeFalsy();
        });

        it('should return false when template is empty but problem statement is not', () => {
            expect(isTemplateOrEmpty('content', '', true)).toBeFalsy();
        });
    });

    describe('isValidRefinementResponse', () => {
        it('should return false for undefined', () => {
            expect(isValidRefinementResponse(undefined)).toBeFalsy();
        });

        it('should return false for empty refined statement', () => {
            expect(isValidRefinementResponse({ refinedProblemStatement: '' })).toBeFalsy();
        });

        it('should return false for whitespace-only refined statement', () => {
            expect(isValidRefinementResponse({ refinedProblemStatement: '   ' })).toBeFalsy();
        });

        it('should return true for valid refined statement', () => {
            expect(isValidRefinementResponse({ refinedProblemStatement: 'Refined content' })).toBeTruthy();
        });
    });

    describe('isValidGenerationResponse', () => {
        it('should return false for undefined', () => {
            expect(isValidGenerationResponse(undefined)).toBeFalsy();
        });

        it('should return false for empty draft', () => {
            expect(isValidGenerationResponse({ draftProblemStatement: '' })).toBeFalsy();
        });

        it('should return false for whitespace-only draft', () => {
            expect(isValidGenerationResponse({ draftProblemStatement: '   ' })).toBeFalsy();
        });

        it('should return true for valid draft', () => {
            expect(isValidGenerationResponse({ draftProblemStatement: 'Generated draft' })).toBeTruthy();
        });
    });

    describe('buildGlobalRefinementRequest', () => {
        it('should build request with trimmed prompt', () => {
            const result = buildGlobalRefinementRequest('problem text', '  make it better  ');
            expect(result.problemStatementText).toBe('problem text');
            expect(result.userPrompt).toBe('make it better');
        });
    });

    describe('buildTargetedRefinementRequest', () => {
        it('should build request from inline refinement event', () => {
            const event: InlineRefinementEvent = {
                instruction: 'Fix this',
                startLine: 1,
                endLine: 3,
                startColumn: 5,
                endColumn: 10,
            };
            const result = buildTargetedRefinementRequest('problem text', event);
            expect(result.problemStatementText).toBe('problem text');
            expect(result.startLine).toBe(1);
            expect(result.endLine).toBe(3);
            expect(result.startColumn).toBe(5);
            expect(result.endColumn).toBe(10);
            expect(result.instruction).toBe('Fix this');
        });
    });

    describe('buildGenerationRequest', () => {
        it('should build request with trimmed prompt', () => {
            const result = buildGenerationRequest('  generate something  ');
            expect(result.userPrompt).toBe('generate something');
        });
    });
});
