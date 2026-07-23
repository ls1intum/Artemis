import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseMode, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { fromFileUploadExerciseDTO, toFileUploadExerciseInputDTO } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import type { FileUploadExerciseDto } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import { toUpdateFileUploadExerciseDTO } from 'app/fileupload/shared/entities/update-file-upload-exercise-dto';

describe('FileUploadExercise DTO adapters', () => {
    it('maps the component model to the scalar create and import contract', () => {
        const course = Object.assign(new Course(), { id: 12 });
        const competency = Object.assign(new Competency(), { id: 34 });
        const exercise = new FileUploadExercise(course, undefined);
        exercise.title = 'Upload';
        exercise.releaseDate = dayjs('2026-01-02T10:00:00.000Z');
        exercise.categories = [new ExerciseCategory('Files', '#123456')];
        exercise.competencyLinks = [new CompetencyExerciseLink(competency, exercise, 0.5)];

        const dto = toFileUploadExerciseInputDTO(exercise);

        expect(dto.courseId).toBe(12);
        expect('course' in dto).toBe(false);
        expect(dto.releaseDate).toBe('2026-01-02T10:00:00.000Z');
        expect(dto.categories).toEqual(['{"category":"Files","color":"#123456"}']);
        expect(dto.competencyLinks).toEqual([{ competency: { id: 34 }, weight: 0.5 }]);
    });

    it('rejects a competency link that cannot be represented by the wire contract', () => {
        const exercise = new FileUploadExercise(undefined, undefined);
        exercise.competencyLinks = [new CompetencyExerciseLink(new Competency(), exercise, 1)];

        expect(() => toFileUploadExerciseInputDTO(exercise)).toThrow('competency link that has no competency ID');
    });

    it('requires the non-null update fields before constructing the update contract', () => {
        const exerciseWithoutId = new FileUploadExercise(undefined, undefined);
        exerciseWithoutId.title = 'Upload';
        expect(() => toUpdateFileUploadExerciseDTO(exerciseWithoutId)).toThrow('update request without an ID');

        const exerciseWithoutTitle = new FileUploadExercise(undefined, undefined);
        exerciseWithoutTitle.id = 56;
        expect(() => toUpdateFileUploadExerciseDTO(exerciseWithoutTitle)).toThrow('update request without a title');
    });

    it('rebuilds dates and minimal exam context from the response contract', () => {
        const dto: FileUploadExerciseDto = {
            id: 56,
            type: ExerciseType.FILE_UPLOAD,
            title: 'Exam upload',
            mode: ExerciseMode.INDIVIDUAL,
            teamMode: false,
            gradingInstructionFeedbackUsed: true,
            releaseDate: '2026-02-01T08:00:00.000Z',
            exerciseGroup: {
                id: 78,
                exam: {
                    id: 90,
                    title: 'Exam',
                    startDate: '2026-02-02T08:00:00.000Z',
                    course: { id: 12, title: 'Course' },
                },
            },
        };

        const exercise = fromFileUploadExerciseDTO(dto);

        expect(exercise).toBeInstanceOf(FileUploadExercise);
        expect(exercise.releaseDate?.toISOString()).toBe('2026-02-01T08:00:00.000Z');
        expect(exercise.exerciseGroup?.id).toBe(78);
        expect(exercise.exerciseGroup?.exam?.id).toBe(90);
        expect(exercise.exerciseGroup?.exam?.startDate?.toISOString()).toBe('2026-02-02T08:00:00.000Z');
        expect(exercise.exerciseGroup?.exam?.course?.id).toBe(12);
        expect(exercise.teamMode).toBe(false);
        expect(exercise.gradingInstructionFeedbackUsed).toBe(true);
    });
});
