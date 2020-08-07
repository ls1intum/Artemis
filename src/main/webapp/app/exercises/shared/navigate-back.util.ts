import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';

export function asseessmentNavigateBack(location: Location, router: Router, exercise: Exercise | null, submission: Submission | null) {
    if (exercise) {
        const course = exercise.course || exercise.exerciseGroup?.exam?.course;

        if (exercise.teamMode && submission) {
            const teamId = (submission.participation as StudentParticipation).team.id;
            router.navigateByUrl(`/courses/${course?.id}/exercises/${exercise.id}/teams/${teamId}`);
        } else {
            router.navigateByUrl(`/course-management/${course?.id}/exercises/${exercise.id}/tutor-dashboard`);
        }
    } else {
        location.back();
    }
}
