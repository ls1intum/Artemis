import type { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import type { Result } from 'app/exercise/shared/entities/result/result.model';
import type { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';

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
    const submission = hydrate({} as Submission, {
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
    return hydrate({} as StudentParticipation, {
        id: dto.id,
        submissionCount: dto.submissionCount,
        exercise: dto.exercise ? lockedExerciseFromDTO(dto.exercise) : undefined,
    });
}

function lockedExerciseFromDTO(dto: CourseLockedExerciseDTO): Exercise {
    return hydrate({} as Exercise, {
        id: dto.id,
        type: dto.type,
        title: dto.title,
    });
}

function lockedResultFromDTO(dto: CourseLockedResultDTO): Result {
    return hydrate({} as Result, {
        score: dto.score,
        completionDate: convertDateStringFromServer(dto.completionDate),
    });
}
