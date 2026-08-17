import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { combineLatest } from 'rxjs';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

export const presentationAssessmentFeatureGuard: CanActivateFn = (route) => {
    const courseId = Number(route.parent?.paramMap.get('courseId') ?? route.paramMap.get('courseId'));
    const router = inject(Router);
    const courseManagementService = inject(CourseManagementService);
    const featureToggleService = inject(FeatureToggleService);

    return combineLatest([courseManagementService.find(courseId), featureToggleService.getFeatureToggleActive(FeatureToggle.PresentationAssessments)]).pipe(
        map(([response, presentationAssessmentsActive]) => {
            if (presentationAssessmentsActive && response.body?.presentationAssessmentsEnabled) {
                return true;
            }
            return router.createUrlTree(['/course-management', courseId, 'lectures']);
        }),
    );
};
