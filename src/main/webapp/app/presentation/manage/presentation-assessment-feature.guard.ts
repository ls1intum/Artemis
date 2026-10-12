import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { catchError, map } from 'rxjs/operators';
import { combineLatest, of } from 'rxjs';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

export const presentationAssessmentFeatureGuard: CanActivateFn = (route) => {
    const courseId = Number(route.parent?.paramMap.get('courseId') ?? route.paramMap.get('courseId'));
    const router = inject(Router);
    const courseManagementService = inject(CourseManagementService);
    const courseStorageService = inject(CourseStorageService);
    const featureToggleService = inject(FeatureToggleService);

    // Selecting another presentation runs the guard again. By then the management container has stored the course, so an enabled
    // setting is taken from there. A stored false is not trusted: courses stored by other pages do not carry the setting and default to false.
    const enabledInCourse = courseStorageService.getCourse(courseId)?.presentationAssessmentsEnabled
        ? of(true)
        : courseManagementService.find(courseId).pipe(map((response) => !!response.body?.presentationAssessmentsEnabled));

    return combineLatest([enabledInCourse, featureToggleService.getFeatureToggleActive(FeatureToggle.PresentationAssessments)]).pipe(
        map(([presentationAssessmentsEnabled, presentationAssessmentsActive]) => {
            if (presentationAssessmentsActive && presentationAssessmentsEnabled) {
                return true;
            }
            // The course overview is open to every role that may open this page; the lectures page is not available to tutors.
            return router.createUrlTree(['/course-management', courseId]);
        }),
        catchError(() => of(router.createUrlTree(['/course-management', courseId]))),
    );
};
