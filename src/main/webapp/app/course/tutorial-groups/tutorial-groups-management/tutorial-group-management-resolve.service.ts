import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Observable, filter, map, pipe } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class TutorialGroupManagementResolve implements Resolve<Course> {
    constructor(private service: CourseManagementService, private router: Router) {}

    resolve(route: ActivatedRouteSnapshot): Observable<Course> {
        return this.service.find(route.params['courseId']).pipe(
            filter((response: HttpResponse<Course>) => response.ok),
            map((course: HttpResponse<Course>) => course.body!),
            pipe(
                tap((course: Course) => {
                    // user has not completed all necessary configuration steps
                    if (!course.tutorialGroupsConfiguration || !course.timeZone) {
                        this.router.navigate(['/course-management', course.id!, 'tutorial-groups-checklist']);
                    }
                }),
            ),
        );
    }
}
