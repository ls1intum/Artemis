import dayjs from 'dayjs/esm';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { UpdateProgrammingExerciseDTO, toUpdateProgrammingExerciseDTO } from './update-programming-exercise-dto.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';

describe('UpdateProgrammingExerciseDTO Model', () => {
    const baseDate = dayjs('2025-06-01T10:00:00.000Z');

    describe('toUpdateProgrammingExerciseDTO', () => {
        it('should convert a full programming exercise to an update DTO', () => {
            const exercise = {
                id: 42,
                title: 'Java Exercise',
                channelName: 'java-ex',
                shortName: 'javaex',
                problemStatement: 'Implement a stack',
                categories: [new ExerciseCategory('easy', '#00ff00')],
                difficulty: DifficultyLevel.EASY,
                maxPoints: 100,
                bonusPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                allowComplaintsForAutomaticAssessments: true,
                allowFeedbackRequests: false,
                presentationScoreEnabled: true,
                secondCorrectionEnabled: false,
                feedbackSuggestionModule: 'athena',
                gradingInstructions: 'Grade carefully',
                releaseDate: baseDate,
                startDate: baseDate.add(1, 'day'),
                dueDate: baseDate.add(7, 'days'),
                assessmentDueDate: baseDate.add(14, 'days'),
                exampleSolutionPublicationDate: baseDate.add(21, 'days'),
                course: { id: 1 },
                gradingCriteria: [
                    {
                        id: 1,
                        title: 'Correctness',
                        structuredGradingInstructions: [
                            {
                                id: 10,
                                credits: 5,
                                gradingScale: 'Good',
                                instructionDescription: 'Works correctly',
                                feedback: 'Correct!',
                                usageCount: 1,
                            },
                        ],
                    },
                ],
                competencyLinks: [
                    {
                        competency: { id: 100 },
                        weight: 1.0,
                    },
                ],
                testRepositoryUri: 'https://git.example.com/test',
                auxiliaryRepositories: [
                    {
                        id: 5,
                        name: 'helpers',
                        checkoutDirectory: '/helpers',
                        repositoryUri: 'https://git.example.com/helpers',
                        description: 'Helper repo',
                    },
                ],
                allowOnlineEditor: true,
                allowOfflineIde: true,
                allowOnlineIde: false,
                staticCodeAnalysisEnabled: true,
                maxStaticCodeAnalysisPenalty: 50,
                programmingLanguage: ProgrammingLanguage.JAVA,
                packageName: 'de.tum.test',
                showTestNamesToStudents: true,
                buildAndTestStudentSubmissionsAfterDueDate: baseDate.add(8, 'days'),
                testCasesChanged: false,
                projectKey: 'JAVA-EX',
                submissionPolicy: undefined,
                projectType: ProjectType.PLAIN_GRADLE,
                releaseTestsWithExampleSolution: true,
                buildConfig: {
                    sequentialTestRuns: false,
                    buildPlanConfiguration: 'config',
                    buildScript: 'script',
                    checkoutSolutionRepository: true,
                    testCheckoutPath: '/test',
                    assignmentCheckoutPath: '/assignment',
                    solutionCheckoutPath: '/solution',
                    timeoutSeconds: 300,
                    dockerFlags: '--memory=512m',
                    theiaImage: 'theia:latest',
                    allowBranching: true,
                    branchRegex: 'feature/.*',
                },
            } as ProgrammingExercise;

            const dto: UpdateProgrammingExerciseDTO = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.id).toBe(42);
            expect(dto.title).toBe('Java Exercise');
            expect(dto.channelName).toBe('java-ex');
            expect(dto.shortName).toBe('javaex');
            expect(dto.problemStatement).toBe('Implement a stack');
            expect(dto.difficulty).toBe(DifficultyLevel.EASY);
            expect(dto.maxPoints).toBe(100);
            expect(dto.bonusPoints).toBe(10);
            expect(dto.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_COMPLETELY);
            expect(dto.allowComplaintsForAutomaticAssessments).toBeTrue();
            expect(dto.allowFeedbackRequests).toBeFalse();
            expect(dto.presentationScoreEnabled).toBeTrue();
            expect(dto.secondCorrectionEnabled).toBeFalse();
            expect(dto.feedbackSuggestionModule).toBe('athena');
            expect(dto.gradingInstructions).toBe('Grade carefully');
            expect(dto.releaseDate).toBe(baseDate.toJSON());
            expect(dto.startDate).toBe(baseDate.add(1, 'day').toJSON());
            expect(dto.dueDate).toBe(baseDate.add(7, 'days').toJSON());
            expect(dto.assessmentDueDate).toBe(baseDate.add(14, 'days').toJSON());
            expect(dto.exampleSolutionPublicationDate).toBe(baseDate.add(21, 'days').toJSON());
            expect(dto.courseId).toBe(1);
            expect(dto.exerciseGroupId).toBeUndefined();
            expect(dto.gradingCriteria).toHaveLength(1);
            expect(dto.gradingCriteria![0].id).toBe(1);
            expect(dto.gradingCriteria![0].title).toBe('Correctness');
            expect(dto.gradingCriteria![0].structuredGradingInstructions).toHaveLength(1);
            expect(dto.gradingCriteria![0].structuredGradingInstructions![0].credits).toBe(5);
            expect(dto.competencyLinks).toHaveLength(1);
            expect(dto.competencyLinks![0].competency.id).toBe(100);
            expect(dto.competencyLinks![0].weight).toBe(1.0);
            expect(dto.testRepositoryUri).toBe('https://git.example.com/test');
            expect(dto.auxiliaryRepositories).toHaveLength(1);
            expect(dto.auxiliaryRepositories![0].name).toBe('helpers');
            expect(dto.allowOnlineEditor).toBeTrue();
            expect(dto.allowOfflineIde).toBeTrue();
            expect(dto.allowOnlineIde).toBeFalse();
            expect(dto.staticCodeAnalysisEnabled).toBeTrue();
            expect(dto.maxStaticCodeAnalysisPenalty).toBe(50);
            expect(dto.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.packageName).toBe('de.tum.test');
            expect(dto.showTestNamesToStudents).toBeTrue();
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBe(baseDate.add(8, 'days').toJSON());
            expect(dto.testCasesChanged).toBeFalse();
            expect(dto.projectKey).toBe('JAVA-EX');
            expect(dto.projectType).toBe(ProjectType.PLAIN_GRADLE);
            expect(dto.releaseTestsWithExampleSolution).toBeTrue();
            expect(dto.buildConfig).toBeDefined();
            expect(dto.buildConfig!.sequentialTestRuns).toBeFalse();
            expect(dto.buildConfig!.buildPlanConfiguration).toBe('config');
            expect(dto.buildConfig!.buildScript).toBe('script');
            expect(dto.buildConfig!.checkoutSolutionRepository).toBeTrue();
            expect(dto.buildConfig!.testCheckoutPath).toBe('/test');
            expect(dto.buildConfig!.assignmentCheckoutPath).toBe('/assignment');
            expect(dto.buildConfig!.solutionCheckoutPath).toBe('/solution');
            expect(dto.buildConfig!.timeoutSeconds).toBe(300);
            expect(dto.buildConfig!.dockerFlags).toBe('--memory=512m');
            expect(dto.buildConfig!.theiaImage).toBe('theia:latest');
            expect(dto.buildConfig!.allowBranching).toBeTrue();
            expect(dto.buildConfig!.branchRegex).toBe('feature/.*');
        });

        it('should set exerciseGroupId for exam exercises and exclude courseId', () => {
            const examExercise = {
                id: 50,
                title: 'Exam Exercise',
                exerciseGroup: { id: 99 },
                course: { id: 1 },
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            } as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(examExercise);

            expect(dto.exerciseGroupId).toBe(99);
            expect(dto.courseId).toBeUndefined();
        });

        it('should apply default values for undefined fields', () => {
            const minimalExercise = {
                id: 1,
                title: 'Minimal',
                includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
                course: { id: 1 },
            } as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(minimalExercise);

            expect(dto.allowOnlineIde).toBeFalse();
            expect(dto.showTestNamesToStudents).toBeFalse();
            expect(dto.releaseTestsWithExampleSolution).toBeFalse();
            expect(dto.bonusPoints).toBe(0);
            expect(dto.buildConfig).toBeUndefined();
        });

        it('should apply build config defaults when build config is present', () => {
            const exercise = {
                id: 1,
                title: 'With Config',
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                course: { id: 1 },
                buildConfig: {},
            } as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.buildConfig).toBeDefined();
            expect(dto.buildConfig!.checkoutSolutionRepository).toBeFalse();
            expect(dto.buildConfig!.timeoutSeconds).toBe(120);
            expect(dto.buildConfig!.allowBranching).toBeFalse();
        });

        it('should set bonusPoints to 0 when includedInOverallScore is not INCLUDED_COMPLETELY', () => {
            const exercise = {
                id: 1,
                title: 'No Bonus',
                bonusPoints: 10,
                includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
                course: { id: 1 },
            } as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.bonusPoints).toBe(0);
        });

        it('should convert categories to JSON strings', () => {
            const exercise = {
                id: 1,
                title: 'Categorized',
                categories: [new ExerciseCategory('easy', '#00ff00'), new ExerciseCategory('java', '#0000ff')],
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                course: { id: 1 },
            } as ProgrammingExercise;

            const dto = toUpdateProgrammingExerciseDTO(exercise);

            expect(dto.categories).toBeDefined();
            expect(dto.categories!).toHaveLength(2);
            dto.categories!.forEach((cat) => {
                expect(() => JSON.parse(cat)).not.toThrow();
            });
        });
    });
});
