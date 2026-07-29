import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Router, RouterStateSnapshot } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Observable, filter, map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';

@Injectable({ providedIn: 'root' })
export class TutorialGroupManagementCourseResolver implements Resolve<Course> {
    private courseManagementService = inject(CourseManagementService);
    private router = inject(Router);
    private alertService = inject(AlertService);

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Course> {
        return this.courseManagementService.find(route.params['courseId']).pipe(
            filter((response: HttpResponse<Course>) => response.ok),
            map((course: HttpResponse<Course>) => course.body!),
            tap((course: Course) => {
                // special case edit configuration
                if (course.tutorialGroupsConfiguration) {
                    const editUrl = '/course-management/' + course.id + '/tutorial-groups/configuration/' + course.tutorialGroupsConfiguration.id + '/edit';
                    if (state.url === editUrl) {
                        return;
                    }
                }
                // user has not completed all necessary configuration steps
                if (!course.tutorialGroupsConfiguration || !course.timeZone) {
                    if (course.isAtLeastInstructor) {
                        void this.router.navigate(['/course-management', course.id!, 'tutorial-groups-checklist']);
                    } else {
                        this.alertService.warning('artemisApp.pages.tutorialGroupsManagement.configurationRequiredForTutor');
                        void this.router.navigate(['/course-management']);
                    }
                }
            }),
        );
    }
}
