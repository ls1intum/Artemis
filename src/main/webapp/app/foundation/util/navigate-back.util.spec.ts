import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { assessmentNavigateBack } from 'app/foundation/util/navigate-back.util';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';

describe('assessmentNavigateBack', () => {
    const location = { back: vi.fn() } as unknown as Location;
    const router = { navigateByUrl: vi.fn() } as unknown as Router;

    const teamExercise = (): Exercise => ({ id: 7, teamMode: true, course: { id: 3 } as Course }) as Exercise;
    const submissionOfTeam = (team?: Team): Submission => ({ participation: { team } as StudentParticipation }) as Submission;

    it('goes back in the history without an exercise', () => {
        assessmentNavigateBack(location, router);

        expect(location.back).toHaveBeenCalledOnce();
    });

    it('opens the team page of a team exercise', () => {
        assessmentNavigateBack(location, router, teamExercise(), submissionOfTeam({ id: 12 } as Team));

        expect(router.navigateByUrl).toHaveBeenCalledWith('/courses/3/exercises/7/teams/12');
    });

    it('opens the assessment dashboard instead of a team page without a team id', () => {
        vi.mocked(router.navigateByUrl).mockClear();

        assessmentNavigateBack(location, router, teamExercise(), submissionOfTeam(undefined));

        expect(router.navigateByUrl).toHaveBeenCalledWith('/course-management/3/assessment-dashboard/7');
    });
});
