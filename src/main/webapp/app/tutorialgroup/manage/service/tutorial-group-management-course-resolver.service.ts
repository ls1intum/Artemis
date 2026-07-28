import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Router, RouterStateSnapshot } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Observable, combineLatest, filter, map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupConfigurationDTO, tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Injectable({ providedIn: 'root' })
export class TutorialGroupManagementCourseResolver implements Resolve<Course> {
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private router = inject(Router);

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Course> {
        const courseId = route.params['courseId'];
        const course$ = this.courseManagementService.find(courseId).pipe(
            filter((response: HttpResponse<Course>) => response.ok),
            map((response: HttpResponse<Course>) => response.body),
            filter((course: Course | null): course is Course => course !== null),
        );
        const configuration$ = this.tutorialGroupsConfigurationService.getOneOfCourse(courseId);

        return combineLatest([course$, configuration$]).pipe(
            map(([course, configurationResponse]: [Course, HttpResponse<TutorialGroupConfigurationDTO>]) => {
                if (configurationResponse.body) {
                    course.tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(configurationResponse.body);
                }
                return course;
            }),
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
                    void this.router.navigate(['/course-management', course.id, 'tutorial-groups-checklist']);
                }
            }),
        );
    }
}
