import dayjs from 'dayjs/esm';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { toUpdateProgrammingExerciseDTO } from './update-programming-exercise-dto.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

describe('UpdateProgrammingExerciseDTO', () => {
    describe('toUpdateProgrammingExerciseDTO', () => {
        let exercise: ProgrammingExercise;

        beforeEach(() => {
            exercise = {
                id: 1,
                type: 'programming',
                title: 'Test Exercise',
                channelName: 'test-channel',
                shortName: 'test',
                problemStatement: 'Solve this',
                categories: [{ category: 'Testing', color: '#000' } as any],
                difficulty: DifficultyLevel.MEDIUM,
                maxPoints: 100,
                bonusPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                allowComplaintsForAutomaticAssessments: true,
                allowFeedbackRequests: false,
                presentationScoreEnabled: true,
                secondCorrectionEnabled: false,
                feedbackSuggestionModule: 'athena',
                gradingInstructions: 'Grade well',
                releaseDate: dayjs('2024-01-01'),
                startDate: dayjs('2024-01-02'),
                dueDate: dayjs('2024-01-10'),
                assessmentDueDate: dayjs('2024-01-15'),
                exampleSolutionPublicationDate: dayjs('2024-01-20'),
                course: { id: 5 } as any,
                gradingCriteria: [
                    {
                        id: 10,
                        title: 'Correctness',
                        structuredGradingInstructions: [
                            {
                                id: 100,
                                credits: 5,
                                gradingScale: 'scale1',
                                instructionDescription: 'desc',
                                feedback: 'feedback',
                                usageCount: 1,
                            },
                        ],
                    },
                ],
                competencyLinks: [{ competency: { id: 20 }, weight: 1.0 }] as any,
                testRepositoryUri: 'https://git.example.com/test.git',
                auxiliaryRepositories: [
                    {
                        id: 30,
                        name: 'aux1',
                        checkoutDirectory: '/aux',
                        repositoryUri: 'https://git.example.com/aux.git',
                        description: 'Aux repo',
                    },
                ],
                allowOnlineEditor: true,
                allowOfflineIde: true,
                allowOnlineIde: true,
                staticCodeAnalysisEnabled: false,
                maxStaticCodeAnalysisPenalty: 50,
                programmingLanguage: ProgrammingLanguage.JAVA,
                packageName: 'de.tum.test',
                showTestNamesToStudents: true,
                buildAndTestStudentSubmissionsAfterDueDate: dayjs('2024-01-11'),
                testCasesChanged: false,
                projectKey: 'TESTKEY',
                submissionPolicy: undefined,
                projectType: ProjectType.PLAIN_GRADLE,
                releaseTestsWithExampleSolution: true,
                buildConfig: {
                    sequentialTestRuns: false,
                    buildPlanConfiguration: 'plan-config',
                    buildScript: 'build.sh',
                    checkoutSolutionRepository: true,
                    testCheckoutPath: '/test',
                    assignmentCheckoutPath: '/assignment',
                    solutionCheckoutPath: '/solution',
                    timeoutSeconds: 300,
                    dockerFlags: '--flag',
                    theiaImage: 'theia:latest',
                    allowBranching: true,
                    branchRegex: 'main|develop',
                } as any,
            } as ProgrammingExercise;
        });

        it('should convert a full programming exercise to DTO', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Test Exercise');
            expect(dto.channelName).toBe('test-channel');
            expect(dto.shortName).toBe('test');
            expect(dto.problemStatement).toBe('Solve this');
            expect(dto.difficulty).toBe(DifficultyLevel.MEDIUM);
            expect(dto.maxPoints).toBe(100);
            expect(dto.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_COMPLETELY);
            expect(dto.allowComplaintsForAutomaticAssessments).toBeTrue();
            expect(dto.allowFeedbackRequests).toBeFalse();
            expect(dto.presentationScoreEnabled).toBeTrue();
            expect(dto.secondCorrectionEnabled).toBeFalse();
            expect(dto.feedbackSuggestionModule).toBe('athena');
            expect(dto.gradingInstructions).toBe('Grade well');
        });

        it('should convert dates to strings', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.releaseDate).toBeDefined();
            expect(dto.startDate).toBeDefined();
            expect(dto.dueDate).toBeDefined();
            expect(dto.assessmentDueDate).toBeDefined();
            expect(dto.exampleSolutionPublicationDate).toBeDefined();
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBeDefined();
        });

        it('should convert grading criteria to DTOs', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.gradingCriteria).toHaveLength(1);
            expect(dto.gradingCriteria![0].id).toBe(10);
            expect(dto.gradingCriteria![0].title).toBe('Correctness');
            expect(dto.gradingCriteria![0].structuredGradingInstructions).toHaveLength(1);
            expect(dto.gradingCriteria![0].structuredGradingInstructions![0].credits).toBe(5);
        });

        it('should convert competency links to DTOs', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.competencyLinks).toHaveLength(1);
            expect(dto.competencyLinks![0].competency.id).toBe(20);
            expect(dto.competencyLinks![0].weight).toBe(1.0);
        });

        it('should convert auxiliary repositories to DTOs', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.auxiliaryRepositories).toHaveLength(1);
            expect(dto.auxiliaryRepositories![0].id).toBe(30);
            expect(dto.auxiliaryRepositories![0].name).toBe('aux1');
        });

        it('should convert build config to DTO', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.buildConfig).toBeDefined();
            expect(dto.buildConfig!.sequentialTestRuns).toBeFalse();
            expect(dto.buildConfig!.checkoutSolutionRepository).toBeTrue();
            expect(dto.buildConfig!.timeoutSeconds).toBe(300);
            expect(dto.buildConfig!.allowBranching).toBeTrue();
            expect(dto.buildConfig!.branchRegex).toBe('main|develop');
        });

        it('should set courseId for course exercises', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.courseId).toBe(5);
            expect(dto.exerciseGroupId).toBeUndefined();
        });

        it('should set exerciseGroupId for exam exercises', () => {
            exercise.exerciseGroup = { id: 42 } as any;
            exercise.course = undefined;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.exerciseGroupId).toBe(42);
            expect(dto.courseId).toBeUndefined();
        });

        it('should handle missing optional fields', () => {
            const minExercise = {
                id: 2,
                type: 'programming',
            } as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(minExercise);

            expect(dto.id).toBe(2);
            expect(dto.gradingCriteria).toBeUndefined();
            expect(dto.competencyLinks).toBeUndefined();
            expect(dto.auxiliaryRepositories).toBeUndefined();
            expect(dto.buildConfig).toBeUndefined();
            expect(dto.allowOnlineIde).toBeFalse();
            expect(dto.showTestNamesToStudents).toBeFalse();
            expect(dto.releaseTestsWithExampleSolution).toBeFalse();
        });

        it('should constrain bonus points based on includedInOverallScore', () => {
            exercise.bonusPoints = 10;
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            const dtoIncluded = toUpdateProgrammingExerciseDTO(exercise);
            expect(dtoIncluded.bonusPoints).toBe(10);

            exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
            const dtoNotIncluded = toUpdateProgrammingExerciseDTO(exercise);
            expect(dtoNotIncluded.bonusPoints).toBe(0);
        });

        it('should convert categories using stringifyExerciseDTOCategories', () => {
            const spy = jest.spyOn(ExerciseService, 'stringifyExerciseDTOCategories');

            toUpdateProgrammingExerciseDTO(exercise);

            expect(spy).toHaveBeenCalledWith(exercise);
        });

        it('should handle build config with defaults', () => {
            exercise.buildConfig = {} as any;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.buildConfig!.checkoutSolutionRepository).toBeFalse();
            expect(dto.buildConfig!.timeoutSeconds).toBe(120);
            expect(dto.buildConfig!.allowBranching).toBeFalse();
        });

        it('should set programming-specific fields', () => {
            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.packageName).toBe('de.tum.test');
            expect(dto.projectKey).toBe('TESTKEY');
            expect(dto.projectType).toBe(ProjectType.PLAIN_GRADLE);
            expect(dto.testRepositoryUri).toBe('https://git.example.com/test.git');
            expect(dto.allowOnlineEditor).toBeTrue();
            expect(dto.allowOfflineIde).toBeTrue();
            expect(dto.allowOnlineIde).toBeTrue();
            expect(dto.staticCodeAnalysisEnabled).toBeFalse();
            expect(dto.maxStaticCodeAnalysisPenalty).toBe(50);
        });
    });
});
