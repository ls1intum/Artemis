import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

export const setBuildPlanUrlForProgrammingParticipations = (profileInfo: ProfileInfo, participations: ProgrammingExerciseStudentParticipation[], projectKey?: string) => {
    if (!projectKey) {
        return;
    }
    participations
        .filter((participation) => participation.buildPlanId)
        .forEach((participation) => {
            participation.buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, participation.buildPlanId!);
        });
};
