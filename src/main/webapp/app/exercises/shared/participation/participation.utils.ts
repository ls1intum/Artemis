import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

export const setBuildPlanUrlForProgrammingParticipation = (
    profileInfo: ProfileInfo,
    participation: ProgrammingExerciseStudentParticipation,
    projectKey?: string,
): ProgrammingExerciseStudentParticipation => {
    if (projectKey && participation.buildPlanId) {
        participation.buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, participation.buildPlanId);
    }
    return participation;
};
