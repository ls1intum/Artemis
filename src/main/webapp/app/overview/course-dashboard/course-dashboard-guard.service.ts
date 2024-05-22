import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';

import { first, lastValueFrom, of, switchMap } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Injectable({
    providedIn: 'root',
})
export class CourseDashboardGuard implements CanActivate {
    constructor(
        private featureToggleService: FeatureToggleService,
        private courseStorageService: CourseStorageService,
        private router: Router,
    ) {}

    /**
     * Check if the client can activate a route.
     * @return true if CourseD is enabled for this instance, false otherwise
     */
    canActivate(route: ActivatedRouteSnapshot): Promise<boolean> {
        return lastValueFrom(
            this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard).pipe(
                first(),
                switchMap((isActive) => {
                    const courseId = route.parent?.paramMap.get('courseId');
                    if (!courseId) {
                        return of(false);
                    }

                    if (isActive) {
                        // Check if course has dashboard enabled
                        const course = this.courseStorageService.getCourse(parseInt(courseId, 10));
                        if (course && course.studentCourseAnalyticsDashboardEnabled) {
                            return of(true);
                        }
                    }

                    this.router.navigate([`/courses/${courseId}/exercises`]);
                    return of(false);
                }),
            ),
        );
    }
}
