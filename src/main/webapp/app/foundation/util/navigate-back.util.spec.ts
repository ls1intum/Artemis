import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { assessmentNavigateBack } from 'app/foundation/util/navigate-back.util';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';

describe('assessmentNavigateBack', () => {
    const location = { back: vi.fn() } as unknown as Location;
    const router = { navigateByUrl: vi.fn() } as unknown as Router;

    beforeEach(() => {
        vi.mocked(router.navigateByUrl).mockClear();
    });

    it('goes back in the history without an exercise', () => {
        assessmentNavigateBack(location, router);

        expect(location.back).toHaveBeenCalledOnce();
    });

    it.each([true, false])('opens the exercise assessment dashboard of a course exercise, teamMode=%s', (teamMode) => {
        assessmentNavigateBack(location, router, { id: 7, teamMode, course: { id: 3 } as Course } as Exercise);

        expect(router.navigateByUrl).toHaveBeenCalledWith('/course-management/3/assessment-dashboard/7');
    });
});
