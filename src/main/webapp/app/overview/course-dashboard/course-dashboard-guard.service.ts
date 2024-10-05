import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';

import { Observable, first, lastValueFrom, map, of, switchMap } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Injectable({
    providedIn: 'root',
})
export class CourseDashboardGuard implements CanActivate {
    private featureToggleService = inject(FeatureToggleService);
    private courseStorageService = inject(CourseStorageService);
    private courseManagementService = inject(CourseManagementService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if CourseD is enabled for this instance, false otherwise
     */
    canActivate(route: ActivatedRouteSnapshot): Promise<boolean> {
        return lastValueFrom(
            this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard).pipe(
                first(),
                switchMap((isActive): Observable<boolean> => {
                    const courseId = route.parent?.paramMap.get('courseId');
                    if (!courseId) {
                        return of(false);
                    }

                    const handleReturn = (value: boolean) => {
                        if (!value) {
                            this.router.navigate([`/courses/${courseId}/exercises`]);
                        }
                        return value;
                    };

                    if (!isActive) {
                        return of(handleReturn(false));
                    }

                    // Check if course has dashboard enabled
                    const course = this.courseStorageService.getCourse(parseInt(courseId, 10));
                    if (course) {
                        return of(handleReturn(course?.studentCourseAnalyticsDashboardEnabled ?? false));
                    }

                    // If course is not in storage, fetch it from the server
                    return this.courseManagementService.find(parseInt(courseId, 10)).pipe(
                        map((res) => {
                            if (res.body) {
                                // Store course in cache
                                this.courseStorageService.updateCourse(res.body);
                            }
                            return handleReturn(res.body?.studentCourseAnalyticsDashboardEnabled ?? false);
                        }),
                    );
                }),
            ),
        );
    }
}
