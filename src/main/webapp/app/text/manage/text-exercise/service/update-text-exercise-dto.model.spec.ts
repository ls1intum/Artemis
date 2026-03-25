/* eslint-disable jest-extended/prefer-to-be-true, jest-extended/prefer-to-be-false */
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { DifficultyLevel, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { toUpdateTextExerciseDTO } from './update-text-exercise-dto.model';

describe('toUpdateTextExerciseDTO', () => {
    it('should convert a fully populated text exercise to an update DTO', () => {
        const course = new Course();
        course.id = 10;

        const exercise = new TextExercise(course, undefined);
        exercise.id = 1;
        exercise.title = 'Essay Writing';
        exercise.shortName = 'essay';
        exercise.maxPoints = 50;
        exercise.bonusPoints = 5;
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.assessmentType = AssessmentType.MANUAL;
        exercise.releaseDate = dayjs('2024-01-01T10:00:00.000Z');
        exercise.startDate = dayjs('2024-01-02T10:00:00.000Z');
        exercise.dueDate = dayjs('2024-01-15T23:59:00.000Z');
        exercise.assessmentDueDate = dayjs('2024-01-20T23:59:00.000Z');
        exercise.exampleSolutionPublicationDate = dayjs('2024-01-21T10:00:00.000Z');
        exercise.difficulty = DifficultyLevel.HARD;
        exercise.mode = ExerciseMode.INDIVIDUAL;
        exercise.problemStatement = 'Write an essay';
        exercise.gradingInstructions = 'Grade based on quality';
        exercise.presentationScoreEnabled = true;
        exercise.secondCorrectionEnabled = true;
        exercise.feedbackSuggestionModule = 'athena';
        exercise.allowComplaintsForAutomaticAssessments = false;
        exercise.allowFeedbackRequests = true;
        exercise.channelName = 'essay-channel';
        exercise.exampleSolution = 'This is a sample essay.';

        exercise.competencyLinks = [{ competency: { id: 200, title: 'Writing' }, weight: 2 }];

        const dto = toUpdateTextExerciseDTO(exercise);

        expect(dto.id).toBe(1);
        expect(dto.title).toBe('Essay Writing');
        expect(dto.shortName).toBe('essay');
        expect(dto.maxPoints).toBe(50);
        expect(dto.bonusPoints).toBe(5);
        expect(dto.releaseDate).toBe(exercise.releaseDate!.toJSON());
        expect(dto.startDate).toBe(exercise.startDate!.toJSON());
        expect(dto.dueDate).toBe(exercise.dueDate!.toJSON());
        expect(dto.assessmentDueDate).toBe(exercise.assessmentDueDate!.toJSON());
        expect(dto.exampleSolutionPublicationDate).toBe(exercise.exampleSolutionPublicationDate!.toJSON());
        expect(dto.difficulty).toBe(DifficultyLevel.HARD);
        expect(dto.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_COMPLETELY);
        expect(dto.problemStatement).toBe('Write an essay');
        expect(dto.gradingInstructions).toBe('Grade based on quality');
        expect(dto.presentationScoreEnabled).toBe(true);
        expect(dto.secondCorrectionEnabled).toBe(true);
        expect(dto.feedbackSuggestionModule).toBe('athena');
        expect(dto.allowComplaintsForAutomaticAssessments).toBe(false);
        expect(dto.allowFeedbackRequests).toBe(true);
        expect(dto.channelName).toBe('essay-channel');
        expect(dto.exampleSolution).toBe('This is a sample essay.');
        expect(dto.courseId).toBe(10);
        expect(dto.exerciseGroupId).toBeUndefined();

        // Competency links (mapped to CompetencyLinkDTO with competency id)
        expect(dto.competencyLinks).toHaveLength(1);
        expect(dto.competencyLinks![0].competency.id).toBe(200);
        expect(dto.competencyLinks![0].weight).toBe(2);
    });

    it('should set exerciseGroupId for exam exercises and leave courseId undefined', () => {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.id = 30;

        const exercise = new TextExercise(undefined, exerciseGroup);
        exercise.id = 2;
        exercise.maxPoints = 10;

        const dto = toUpdateTextExerciseDTO(exercise);

        expect(dto.exerciseGroupId).toBe(30);
        expect(dto.courseId).toBeUndefined();
    });

    it('should set bonusPoints to 0 when includedInOverallScore is not INCLUDED_COMPLETELY', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 3;
        exercise.maxPoints = 10;
        exercise.bonusPoints = 5;
        exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;

        const dto = toUpdateTextExerciseDTO(exercise);

        expect(dto.bonusPoints).toBe(0);
    });

    it('should handle undefined dates', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 4;
        exercise.maxPoints = 10;

        const dto = toUpdateTextExerciseDTO(exercise);

        expect(dto.releaseDate).toBeUndefined();
        expect(dto.startDate).toBeUndefined();
        expect(dto.dueDate).toBeUndefined();
        expect(dto.assessmentDueDate).toBeUndefined();
        expect(dto.exampleSolutionPublicationDate).toBeUndefined();
    });

    it('should handle empty competency links', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 5;
        exercise.maxPoints = 10;
        exercise.competencyLinks = [];

        const dto = toUpdateTextExerciseDTO(exercise);

        expect(dto.competencyLinks).toEqual([]);
    });
});
