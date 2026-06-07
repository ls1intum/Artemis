import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class CourseManagementResolve implements Resolve<Course> {
    private courseManagementService = inject(CourseManagementService);

    /**
     * Resolves the route by extracting the courseId and returns the course with that Id if it exists
     * and creates a new course otherwise
     * @param route - contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<Course> {
        const courseId = this.findCourseId(route);
        if (courseId) {
            return this.courseManagementService.find(Number(courseId)).pipe(
                filter((response: HttpResponse<Course>) => response.ok),
                map((course: HttpResponse<Course>) => course.body!),
            );
        }
        return of(new Course());
    }

    private findCourseId(route: ActivatedRouteSnapshot): string | undefined {
        let current: ActivatedRouteSnapshot | null = route;
        while (current) {
            if (current.params['courseId']) {
                return current.params['courseId'];
            }
            current = current.parent;
        }
        return undefined;
    }
}
