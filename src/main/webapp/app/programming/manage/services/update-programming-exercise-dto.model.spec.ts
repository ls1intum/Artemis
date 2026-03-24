import { toUpdateProgrammingExerciseDTO } from './update-programming-exercise-dto.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';

describe('UpdateProgrammingExerciseDTO', () => {
    describe('toUpdateProgrammingExerciseDTO', () => {
        beforeEach(() => {
            jest.spyOn(ExerciseService, 'setBonusPointsConstrainedByIncludedInOverallScore').mockImplementation((exercise) => exercise);
            jest.spyOn(ExerciseService, 'stringifyExerciseDTOCategories').mockReturnValue(['cat1']);
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should convert a programming exercise to an update DTO', () => {
            const releaseDate = dayjs('2024-01-01T10:00:00');
            const dueDate = dayjs('2024-01-15T23:59:00');
            const buildAndTestDate = dayjs('2024-01-16T00:00:00');

            const exercise = {
                id: 42,
                title: 'Programming Exercise',
                channelName: 'prog-channel',
                shortName: 'PE',
                problemStatement: 'Write code',
                difficulty: DifficultyLevel.HARD,
                maxPoints: 100,
                bonusPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                allowComplaintsForAutomaticAssessments: true,
                allowFeedbackRequests: false,
                presentationScoreEnabled: true,
                secondCorrectionEnabled: false,
                gradingInstructions: 'Grade well',
                releaseDate,
                dueDate,
                course: { id: 5 },
                competencyLinks: [{ competency: { id: 10 }, weight: 1 }],
                gradingCriteria: [
                    {
                        id: 1,
                        title: 'Criterion',
                        structuredGradingInstructions: [{ id: 1, credits: 5, gradingScale: 'scale', instructionDescription: 'desc', feedback: 'fb', usageCount: 1 }],
                    },
                ],
                auxiliaryRepositories: [{ id: 1, name: 'aux', checkoutDirectory: '/aux', repositoryUri: 'uri', description: 'desc' }],
                allowOnlineEditor: true,
                allowOfflineIde: true,
                allowOnlineIde: false,
                staticCodeAnalysisEnabled: true,
                maxStaticCodeAnalysisPenalty: 50,
                programmingLanguage: ProgrammingLanguage.JAVA,
                packageName: 'com.example',
                showTestNamesToStudents: true,
                buildAndTestStudentSubmissionsAfterDueDate: buildAndTestDate,
                projectKey: 'PROJ',
                projectType: ProjectType.PLAIN_MAVEN,
                releaseTestsWithExampleSolution: false,
                buildConfig: {
                    sequentialTestRuns: false,
                    checkoutSolutionRepository: true,
                    timeoutSeconds: 120,
                    allowBranching: false,
                    buildScript: 'mvn test',
                },
            } as any as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.id).toBe(42);
            expect(dto.title).toBe('Programming Exercise');
            expect(dto.channelName).toBe('prog-channel');
            expect(dto.shortName).toBe('PE');
            expect(dto.problemStatement).toBe('Write code');
            expect(dto.difficulty).toBe(DifficultyLevel.HARD);
            expect(dto.maxPoints).toBe(100);
            expect(dto.bonusPoints).toBe(10);
            expect(dto.releaseDate).toBe(releaseDate.toJSON());
            expect(dto.dueDate).toBe(dueDate.toJSON());
            expect(dto.courseId).toBe(5);
            expect(dto.exerciseGroupId).toBeUndefined();
            expect(dto.competencyLinks).toHaveLength(1);
            expect(dto.gradingCriteria).toHaveLength(1);
            expect(dto.gradingCriteria![0].structuredGradingInstructions).toHaveLength(1);
            expect(dto.auxiliaryRepositories).toHaveLength(1);
            expect(dto.allowOnlineEditor).toBeTrue();
            expect(dto.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.showTestNamesToStudents).toBeTrue();
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBe(buildAndTestDate.toJSON());
            expect(dto.buildConfig).toBeDefined();
            expect(dto.buildConfig!.checkoutSolutionRepository).toBeTrue();
            expect(dto.buildConfig!.buildScript).toBe('mvn test');
            expect(dto.categories).toEqual(['cat1']);
        });

        it('should set exerciseGroupId for exam exercises', () => {
            const exercise = {
                id: 1,
                exerciseGroup: { id: 99 },
                course: { id: 5 },
                buildConfig: undefined,
            } as any as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.exerciseGroupId).toBe(99);
            expect(dto.courseId).toBeUndefined();
            expect(dto.buildConfig).toBeUndefined();
        });

        it('should use default values for undefined boolean fields', () => {
            const exercise = {
                id: 1,
            } as any as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.allowOnlineIde).toBeFalse();
            expect(dto.showTestNamesToStudents).toBeFalse();
            expect(dto.releaseTestsWithExampleSolution).toBeFalse();
        });
    });
});
