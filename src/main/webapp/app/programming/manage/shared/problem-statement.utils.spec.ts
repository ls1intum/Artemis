import {
    InlineRefinementEvent,
    buildGenerationRequest,
    buildGlobalRefinementRequest,
    buildTargetedRefinementRequest,
    deriveDraftMetadataPrefill,
    deriveProposedPackageName,
    extractProblemStatementTitle,
    getCourseId,
    isTemplateOrEmpty,
    isValidGenerationResponse,
    isValidRefinementResponse,
    normalizeString,
} from './problem-statement.utils';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
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

    describe('extractProblemStatementTitle', () => {
        it('should extract the first level-1 heading and strip inline formatting', () => {
            expect(extractProblemStatementTitle('# Summarizing *Bicycle-Share* Trips\n\n## Tasks')).toBe('Summarizing Bicycle-Share Trips');
        });

        it('should return undefined when there is no heading or no statement', () => {
            expect(extractProblemStatementTitle('plain text without heading')).toBeUndefined();
            expect(extractProblemStatementTitle(undefined)).toBeUndefined();
            expect(extractProblemStatementTitle('#    ')).toBeUndefined();
        });

        it('should ignore deeper headings before the level-1 heading', () => {
            expect(extractProblemStatementTitle('intro\n# Event Scheduler\n# Second')).toBe('Event Scheduler');
        });
    });

    describe('deriveProposedPackageName', () => {
        it('should join title words into a lowercase single segment for Java', () => {
            expect(deriveProposedPackageName('Summarizing Bicycle-Share Trips', ProgrammingLanguage.JAVA)).toBe('summarizingbicyclesharetrips');
        });

        it('should fold accents and drop non-ASCII characters', () => {
            expect(deriveProposedPackageName('Café Menü Planner', ProgrammingLanguage.JAVA)).toBe('cafemenuplanner');
        });

        it('should not start with a digit', () => {
            expect(deriveProposedPackageName('2048 Game', ProgrammingLanguage.JAVA)).toBe('game');
        });

        it('should escape reserved words by appending exercise', () => {
            expect(deriveProposedPackageName('Switch', ProgrammingLanguage.JAVA)).toBe('switchexercise');
        });

        it('should cap the length', () => {
            const derived = deriveProposedPackageName('A '.repeat(60) + 'Very Long Exercise Title Indeed', ProgrammingLanguage.JAVA);
            expect(derived!.length).toBeLessThanOrEqual(32);
        });

        it('should produce a PascalCase app name for Swift', () => {
            expect(deriveProposedPackageName('robot rover state', ProgrammingLanguage.SWIFT)).toBe('RobotRoverState');
        });

        it('should respect the blackbox pattern for Java blackbox projects', () => {
            expect(deriveProposedPackageName('Transit Fare Ledger', ProgrammingLanguage.JAVA, ProjectType.MAVEN_BLACKBOX)).toBe('transitfareledger');
        });

        it('should return undefined for languages without a package concept or unusable titles', () => {
            expect(deriveProposedPackageName('Bicycle Share', ProgrammingLanguage.PYTHON)).toBeUndefined();
            expect(deriveProposedPackageName('---', ProgrammingLanguage.JAVA)).toBeUndefined();
            expect(deriveProposedPackageName('123', ProgrammingLanguage.JAVA)).toBeUndefined();
        });
    });

    describe('deriveDraftMetadataPrefill', () => {
        const draft = '# Warehouse Batch Allocation\n\nIntro text.';

        it('should propose title and package name for blank fields on a new exercise without mutating it', () => {
            const exercise = { programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            const prefill = deriveDraftMetadataPrefill(exercise, draft);
            expect(prefill).toEqual({ title: 'Warehouse Batch Allocation', packageName: 'warehousebatchallocation' });
            expect(exercise.title).toBeUndefined();
            expect(exercise.packageName).toBeUndefined();
        });

        it('should sanitize punctuation and accents that are invalid in exercise titles', () => {
            const exercise = { programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, '# Café Sorting: Dates!')).toEqual({ title: 'Cafe Sorting Dates', packageName: 'cafesortingdates' });
        });

        it('should not propose a title that is shorter than the exercise form minimum', () => {
            const exercise = { programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, '# AI')).toEqual({ packageName: 'ai' });
        });

        it('should never propose over instructor-typed values', () => {
            const exercise = { title: 'My Title', packageName: 'my.pkg', programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, draft)).toBeUndefined();
        });

        it('should propose only the missing field', () => {
            const exercise = { title: 'My Title', programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, draft)).toEqual({ packageName: 'warehousebatchallocation' });
        });

        it('should not propose anything for an already created exercise', () => {
            const exercise = { id: 42, programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, draft)).toBeUndefined();
        });

        it('should propose only the title when the language has no package concept', () => {
            const exercise = { programmingLanguage: ProgrammingLanguage.PYTHON } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, draft)).toEqual({ title: 'Warehouse Batch Allocation' });
        });

        it('should propose nothing when the draft has no title heading', () => {
            const exercise = { programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
            expect(deriveDraftMetadataPrefill(exercise, 'no heading here')).toBeUndefined();
        });
    });
});
