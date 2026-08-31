import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Router, RouterStateSnapshot } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { EMPTY, Observable, combineLatest, filter, map, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, HttpStatusCode } from '@angular/common/http';
import { catchError, switchMap } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupConfigurationDTO, tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Injectable({ providedIn: 'root' })
export class TutorialGroupManagementCourseResolver implements Resolve<Course> {
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private router = inject(Router);
    private alertService = inject(AlertService);

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Course> {
        const courseId = route.params['courseId'];
        const course$ = this.courseManagementService.find(courseId).pipe(
            filter((response: HttpResponse<Course>) => response.ok),
            map((response: HttpResponse<Course>) => response.body ?? undefined),
            filter((course: Course | undefined): course is Course => course !== undefined),
        );
        const configuration$ = this.tutorialGroupsConfigurationService.getOneOfCourse(courseId);

        return combineLatest([course$, configuration$]).pipe(
            map(([course, configurationResponse]: [Course, HttpResponse<TutorialGroupConfigurationDTO>]) => {
                if (configurationResponse.body) {
                    course.tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(configurationResponse.body);
                }
                return course;
            }),
            // Every redirect returns EMPTY so the resolver completes without emitting and Angular cancels this
            // navigation. Emitting as well would activate the target route alongside the redirect.
            switchMap((course: Course) => {
                if (!course.isAtLeastTutor) {
                    this.alertService.error('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
                    void this.router.navigate(['/courses']);
                    return EMPTY;
                }
                if (course.tutorialGroupsConfiguration) {
                    const editUrl = '/course-management/' + course.id + '/tutorial-groups/configuration/' + course.tutorialGroupsConfiguration.id + '/edit';
                    if (state.url === editUrl) {
                        return of(course);
                    }
                }
                if (!course.tutorialGroupsConfiguration || !course.timeZone) {
                    if (course.isAtLeastInstructor) {
                        void this.router.navigate(['/course-management', course.id, 'tutorial-groups-checklist']);
                    } else {
                        this.alertService.warning('artemisApp.pages.tutorialGroupsManagement.configurationRequiredForTutor');
                        void this.router.navigate(['/courses']);
                    }
                    return EMPTY;
                }
                return of(course);
            }),
            // Both endpoints require at least student in the course, so a user below that gets a 403 and the
            // isAtLeastTutor check above is never reached. Without this the navigation just fails and drops the
            // user on the base URL with no explanation.
            catchError((error: unknown) => {
                if (error instanceof HttpErrorResponse && error.status === HttpStatusCode.Forbidden) {
                    this.alertService.error('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
                    void this.router.navigate(['/courses']);
                    return EMPTY;
                }
                return throwError(() => error);
            }),
        );
    }
}
