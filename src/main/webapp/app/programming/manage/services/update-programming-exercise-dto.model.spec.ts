import dayjs from 'dayjs/esm';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { toUpdateProgrammingExerciseDTO } from './update-programming-exercise-dto.model';

describe('toUpdateProgrammingExerciseDTO', () => {
    it('should convert a fully populated programming exercise to an update DTO', () => {
        const course = new Course();
        course.id = 10;

        const exercise = new ProgrammingExercise(course, undefined);
        exercise.id = 1;
        exercise.title = 'Sorting Algorithms';
        exercise.channelName = 'sorting';
        exercise.shortName = 'sort';
        exercise.problemStatement = 'Implement sorting';
        exercise.difficulty = DifficultyLevel.MEDIUM;
        exercise.maxPoints = 100;
        exercise.bonusPoints = 10;
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.allowComplaintsForAutomaticAssessments = true;
        exercise.allowFeedbackRequests = true;
        exercise.presentationScoreEnabled = false;
        exercise.secondCorrectionEnabled = false;
        exercise.gradingInstructions = 'Grade carefully';
        exercise.releaseDate = dayjs('2024-01-01T10:00:00.000Z');
        exercise.startDate = dayjs('2024-01-02T10:00:00.000Z');
        exercise.dueDate = dayjs('2024-01-15T23:59:00.000Z');
        exercise.assessmentDueDate = dayjs('2024-01-20T23:59:00.000Z');
        exercise.exampleSolutionPublicationDate = dayjs('2024-01-21T10:00:00.000Z');
        exercise.testRepositoryUri = 'https://git.example.com/test';
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        exercise.allowOnlineIde = false;
        exercise.staticCodeAnalysisEnabled = true;
        exercise.maxStaticCodeAnalysisPenalty = 50;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.packageName = 'de.tum.sort';
        exercise.showTestNamesToStudents = true;
        exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs('2024-01-16T00:00:00.000Z');
        exercise.testCasesChanged = false;
        exercise.projectKey = 'SORT';
        exercise.projectType = ProjectType.PLAIN_GRADLE;
        exercise.releaseTestsWithExampleSolution = true;

        exercise.auxiliaryRepositories = [{ id: 1, name: 'utils', checkoutDirectory: '/utils', repositoryUri: 'https://git.example.com/utils', description: 'Utility repo' }];

        exercise.gradingCriteria = [
            {
                id: 1,
                title: 'Correctness',
                structuredGradingInstructions: [{ id: 10, credits: 5, gradingScale: 'good', instructionDescription: 'Works correctly', feedback: 'Correct', usageCount: 1 }],
            },
        ];

        exercise.competencyLinks = [{ competency: { id: 100, title: 'Algorithms' }, weight: 1 }];

        exercise.buildConfig = {
            sequentialTestRuns: false,
            buildPlanConfiguration: '{}',
            buildScript: 'gradle test',
            checkoutSolutionRepository: true,
            testCheckoutPath: '/test',
            assignmentCheckoutPath: '/assignment',
            solutionCheckoutPath: '/solution',
            timeoutSeconds: 300,
            dockerFlags: '--memory=512m',
            theiaImage: 'eclipse/theia',
            allowBranching: true,
            branchRegex: 'feature/*',
        };

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        expect(dto.id).toBe(1);
        expect(dto.title).toBe('Sorting Algorithms');
        expect(dto.channelName).toBe('sorting');
        expect(dto.shortName).toBe('sort');
        expect(dto.problemStatement).toBe('Implement sorting');
        expect(dto.difficulty).toBe(DifficultyLevel.MEDIUM);
        expect(dto.maxPoints).toBe(100);
        expect(dto.bonusPoints).toBe(10);
        expect(dto.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_COMPLETELY);
        expect(dto.releaseDate).toBe(exercise.releaseDate!.toJSON());
        expect(dto.startDate).toBe(exercise.startDate!.toJSON());
        expect(dto.dueDate).toBe(exercise.dueDate!.toJSON());
        expect(dto.assessmentDueDate).toBe(exercise.assessmentDueDate!.toJSON());
        expect(dto.courseId).toBe(10);
        expect(dto.exerciseGroupId).toBeUndefined();
        expect(dto.testRepositoryUri).toBe('https://git.example.com/test');
        expect(dto.allowOnlineEditor).toBeTrue();
        expect(dto.allowOfflineIde).toBeTrue();
        expect(dto.allowOnlineIde).toBeFalse();
        expect(dto.staticCodeAnalysisEnabled).toBeTrue();
        expect(dto.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
        expect(dto.showTestNamesToStudents).toBeTrue();
        expect(dto.releaseTestsWithExampleSolution).toBeTrue();
        expect(dto.projectType).toBe(ProjectType.PLAIN_GRADLE);

        // Auxiliary repositories
        expect(dto.auxiliaryRepositories).toHaveLength(1);
        expect(dto.auxiliaryRepositories![0].name).toBe('utils');

        // Grading criteria
        expect(dto.gradingCriteria).toHaveLength(1);
        expect(dto.gradingCriteria![0].title).toBe('Correctness');
        expect(dto.gradingCriteria![0].structuredGradingInstructions).toHaveLength(1);
        expect(dto.gradingCriteria![0].structuredGradingInstructions![0].credits).toBe(5);

        // Competency links
        expect(dto.competencyLinks).toHaveLength(1);
        expect(dto.competencyLinks![0].competency.id).toBe(100);

        // Build config
        expect(dto.buildConfig).toBeDefined();
        expect(dto.buildConfig!.sequentialTestRuns).toBeFalse();
        expect(dto.buildConfig!.checkoutSolutionRepository).toBeTrue();
        expect(dto.buildConfig!.timeoutSeconds).toBe(300);
        expect(dto.buildConfig!.allowBranching).toBeTrue();
        expect(dto.buildConfig!.branchRegex).toBe('feature/*');
    });

    it('should set exerciseGroupId for exam exercises and leave courseId undefined', () => {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.id = 20;

        const exercise = new ProgrammingExercise(undefined, exerciseGroup);
        exercise.id = 2;
        exercise.allowOnlineIde = false;
        exercise.showTestNamesToStudents = false;
        exercise.releaseTestsWithExampleSolution = false;

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        expect(dto.exerciseGroupId).toBe(20);
        expect(dto.courseId).toBeUndefined();
    });

    it('should apply defaults for undefined boolean fields', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 3;

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        expect(dto.allowOnlineIde).toBeFalse();
        expect(dto.showTestNamesToStudents).toBeFalse();
        expect(dto.releaseTestsWithExampleSolution).toBeFalse();
        expect(dto.bonusPoints).toBe(0);
    });

    it('should handle default build config from constructor', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 4;

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        // Constructor always creates a buildConfig
        expect(dto.buildConfig).toBeDefined();
        expect(dto.buildConfig!.checkoutSolutionRepository).toBeFalse();
        expect(dto.buildConfig!.timeoutSeconds).toBe(120);
        expect(dto.buildConfig!.allowBranching).toBeFalse();
    });

    it('should handle undefined dates', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 6;

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        expect(dto.releaseDate).toBeUndefined();
        expect(dto.startDate).toBeUndefined();
        expect(dto.dueDate).toBeUndefined();
        expect(dto.assessmentDueDate).toBeUndefined();
        expect(dto.exampleSolutionPublicationDate).toBeUndefined();
        expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBeUndefined();
    });
});
