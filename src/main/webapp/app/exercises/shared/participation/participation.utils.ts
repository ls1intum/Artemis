import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

export const setBuildPlanUrlForProgrammingParticipations = (profileInfo: ProfileInfo, participations: ProgrammingExerciseStudentParticipation[], projectKey?: string) => {
    for (let i = 0; i < participations.length; i++) {
        if (projectKey && participations[i].buildPlanId) {
            participations[i].buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, participations[i].buildPlanId!);
        }
    }
};
