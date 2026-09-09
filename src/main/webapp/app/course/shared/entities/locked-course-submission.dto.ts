import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { createExercise } from 'app/course/shared/entities/course-content-response.dto';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';

export interface LockedCourseSubmissionDTO {
    id: number;
    submissionDate?: string;
    submissionExerciseType: SubmissionExerciseType;
    participation?: CourseLockedParticipationDTO;
    latestResult?: CourseLockedResultDTO;
}

export interface CourseLockedParticipationDTO {
    id: number;
    submissionCount?: number;
    exercise?: CourseLockedExerciseDTO;
}

export interface CourseLockedExerciseDTO {
    id: number;
    type: ExerciseType;
    title: string;
}

export interface CourseLockedResultDTO {
    score?: number;
    completionDate?: string;
}

export function lockedCourseSubmissionFromDTO(dto: LockedCourseSubmissionDTO): Submission {
    const submission = hydrate(createSubmission(dto.submissionExerciseType), {
        id: dto.id,
        submissionDate: convertDateStringFromServer(dto.submissionDate),
        submissionExerciseType: dto.submissionExerciseType,
        participation: dto.participation ? lockedParticipationFromDTO(dto.participation) : undefined,
        latestResult: dto.latestResult ? lockedResultFromDTO(dto.latestResult) : undefined,
    });
    return submission;
}

function lockedParticipationFromDTO(dto?: CourseLockedParticipationDTO): StudentParticipation | undefined {
    if (!dto) {
        return undefined;
    }
    return hydrate(new StudentParticipation(), {
        id: dto.id,
        submissionCount: dto.submissionCount,
        exercise: dto.exercise ? lockedExerciseFromDTO(dto.exercise) : undefined,
    });
}

function lockedExerciseFromDTO(dto: CourseLockedExerciseDTO): Exercise {
    const exercise = createExercise(dto.type);
    return hydrate(exercise, {
        id: dto.id,
        title: dto.title,
    });
}

function lockedResultFromDTO(dto: CourseLockedResultDTO): Result {
    return hydrate(new Result(), {
        score: dto.score,
        completionDate: convertDateStringFromServer(dto.completionDate),
    });
}

function createSubmission(type: SubmissionExerciseType): Submission {
    switch (type) {
        case SubmissionExerciseType.PROGRAMMING:
            return new ProgrammingSubmission();
        case SubmissionExerciseType.MODELING:
            return new ModelingSubmission();
        case SubmissionExerciseType.QUIZ:
            return new QuizSubmission();
        case SubmissionExerciseType.TEXT:
            return new TextSubmission();
        case SubmissionExerciseType.FILE_UPLOAD:
            return new FileUploadSubmission();
    }
}
