import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs/operators';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';

export const presentationAssessmentFeatureGuard: CanActivateFn = (route) => {
    const courseId = Number(route.parent?.paramMap.get('courseId') ?? route.paramMap.get('courseId'));
    const router = inject(Router);
    const courseManagementService = inject(CourseManagementService);

    return courseManagementService.find(courseId).pipe(
        map((response) => {
            if (response.body?.presentationAssessmentsEnabled) {
                return true;
            }
            return router.createUrlTree(['/course-management', courseId, 'lectures']);
        }),
    );
};
