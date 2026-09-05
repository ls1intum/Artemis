import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseMode, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { fromFileUploadExerciseDTO, toCompetencyLinkDTO, toFileUploadExerciseInputDTO } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import type { FileUploadExerciseDto } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import { toUpdateFileUploadExerciseDTO } from 'app/fileupload/shared/entities/update-file-upload-exercise-dto';
import { parseJson } from 'app/foundation/util/json.util';

describe('FileUploadExercise DTO adapters', () => {
    it('maps the component model to the scalar create and import contract', () => {
        const course = new Course();
        course.id = 12;
        const competency = new Competency();
        competency.id = 34;
        const exercise = new FileUploadExercise(course, undefined);
        exercise.title = 'Upload';
        exercise.releaseDate = dayjs('2026-01-02T10:00:00.000Z');
        exercise.categories = [new ExerciseCategory('Files', '#123456')];
        exercise.competencyLinks = [new CompetencyExerciseLink(competency, exercise, 0.5)];
        exercise.teamAssignmentConfig = new TeamAssignmentConfig();
        exercise.teamAssignmentConfig.id = 45;
        exercise.teamAssignmentConfig.minTeamSize = 2;
        exercise.teamAssignmentConfig.maxTeamSize = 4;
        exercise.plagiarismDetectionConfig = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
            similarityThreshold: 90,
            minimumScore: 10,
            minimumSize: 50,
        };

        const dto = toFileUploadExerciseInputDTO(exercise);

        expect(dto.courseId).toBe(12);
        expect('course' in dto).toBe(false);
        expect(dto.releaseDate).toBe('2026-01-02T10:00:00.000Z');
        expect(dto.categories?.map((category) => parseJson(category))).toEqual([{ category: 'Files', color: '#123456' }]);
        expect(dto.teamAssignmentConfig).toEqual({ id: 45, minTeamSize: 2, maxTeamSize: 4 });
        expect(dto.competencyLinks).toEqual([{ competency: { id: 34 }, weight: 0.5 }]);
        expect(dto.plagiarismDetectionConfig).toEqual(exercise.plagiarismDetectionConfig);
        expect(dto.plagiarismDetectionConfig).not.toBe(exercise.plagiarismDetectionConfig);
    });

    it('uses the update-compatible fallback for a competency link without a weight', () => {
        const competency = new Competency();
        competency.id = 34;
        const exercise = new FileUploadExercise(undefined, undefined);
        const link = new CompetencyExerciseLink(competency, exercise, 1);
        Reflect.deleteProperty(link, 'weight');
        exercise.competencyLinks = [link];

        expect(toFileUploadExerciseInputDTO(exercise).competencyLinks).toEqual([{ competency: { id: 34 }, weight: 1 }]);
    });

    it('rejects a competency link that cannot be represented by the wire contract', () => {
        const exercise = new FileUploadExercise(undefined, undefined);
        exercise.competencyLinks = [new CompetencyExerciseLink(new Competency(), exercise, 1)];

        expect(() => toFileUploadExerciseInputDTO(exercise)).toThrow('competency link that has no competency ID');
    });

    it('rejects a competency link without a weight when no fallback is provided', () => {
        const competency = new Competency();
        competency.id = 34;
        const link = new CompetencyExerciseLink(competency, undefined, 1);
        Reflect.deleteProperty(link, 'weight');

        expect(() => toCompetencyLinkDTO(link)).toThrow('competency link that has no weight');
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
            exerciseVariantGroup: {
                id: 67,
                title: 'Variants',
                maxPoints: 20,
                dueDate: '2026-02-03T08:00:00.000Z',
            },
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
        expect(exercise.exerciseVariantGroup?.id).toBe(67);
        expect(exercise.exerciseVariantGroup?.dueDate?.toISOString()).toBe('2026-02-03T08:00:00.000Z');
        expect(exercise.teamMode).toBe(false);
        expect(exercise.gradingInstructionFeedbackUsed).toBe(true);
    });

    it('rebuilds full response associations including competency titles', () => {
        const plagiarismDetectionConfig = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
            similarityThreshold: 90,
            minimumScore: 10,
            minimumSize: 50,
        };
        const dto: FileUploadExerciseDto = {
            id: 56,
            type: ExerciseType.FILE_UPLOAD,
            title: 'Team upload',
            mode: ExerciseMode.TEAM,
            teamMode: true,
            gradingInstructionFeedbackUsed: false,
            categories: [JSON.stringify({ category: 'Files', color: '#123456' }), 'invalid JSON'],
            teamAssignmentConfig: { id: 45, minTeamSize: 2, maxTeamSize: 4 },
            course: { id: 12, title: 'Course', shortName: 'COURSE', testCourse: true, presentationScore: 2, accuracyOfScores: 1 },
            gradingCriteria: [
                {
                    id: 67,
                    title: 'Quality',
                    structuredGradingInstructions: [
                        {
                            id: 78,
                            credits: 2.5,
                            gradingScale: 'GOOD',
                            instructionDescription: 'Well structured',
                            feedback: 'Good work',
                            usageCount: 3,
                        },
                    ],
                },
            ],
            competencyLinks: [{ competency: { id: 34, title: 'Quality Assurance' }, weight: 0.5 }],
            plagiarismDetectionConfig,
        };

        const exercise = fromFileUploadExerciseDTO(dto);

        expect(exercise.course).toBeInstanceOf(Course);
        expect(exercise.course).toMatchObject({ id: 12, title: 'Course', shortName: 'COURSE', testCourse: true, presentationScore: 2, accuracyOfScores: 1 });
        expect(exercise.categories).toEqual([new ExerciseCategory('Files', '#123456')]);
        expect(exercise.teamAssignmentConfig).toBeInstanceOf(TeamAssignmentConfig);
        expect(exercise.teamAssignmentConfig).toMatchObject({ id: 45, minTeamSize: 2, maxTeamSize: 4 });
        expect(exercise.gradingCriteria?.[0]).toBeInstanceOf(GradingCriterion);
        expect(exercise.gradingCriteria?.[0].structuredGradingInstructions[0]).toBeInstanceOf(GradingInstruction);
        expect(exercise.gradingCriteria?.[0].structuredGradingInstructions[0]).toMatchObject({
            id: 78,
            credits: 2.5,
            gradingScale: 'GOOD',
            instructionDescription: 'Well structured',
            feedback: 'Good work',
            usageCount: 3,
        });
        expect(exercise.competencyLinks?.[0].competency).toBeInstanceOf(Competency);
        expect(exercise.competencyLinks?.[0].competency).toMatchObject({ id: 34, title: 'Quality Assurance' });
        expect(exercise.plagiarismDetectionConfig).toEqual(plagiarismDetectionConfig);
        expect(exercise.plagiarismDetectionConfig).not.toBe(plagiarismDetectionConfig);
    });
});
