import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { CourseManagementComponent } from './course-management.component';
import { CourseDetailComponent } from './course-detail.component';
import { CourseUpdateComponent } from './course-update.component';
import { CourseExercisesOverviewComponent } from './course-exercises-overview.component';
import { CourseGroupComponent } from 'app/course/manage/course-group.component';

@Injectable({ providedIn: 'root' })
export class CourseResolve implements Resolve<Course> {
    constructor(private service: CourseManagementService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Course> {
        const id = route.params['courseId'] ? route.params['courseId'] : null;
        if (id) {
            return this.service.find(id).pipe(
                filter((response: HttpResponse<Course>) => response.ok),
                map((course: HttpResponse<Course>) => course.body!),
            );
        }
        return of(new Course());
    }
}

export const courseManagementRoute: Routes = [
    {
        path: '',
        component: CourseManagementComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: CourseUpdateComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: ['ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/view',
        component: CourseDetailComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/exercises',
        component: CourseExercisesOverviewComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_TA', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/edit',
        component: CourseUpdateComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/groups/:courseGroup',
        component: CourseGroupComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const COURSE_MANAGEMENT_ROUTES = [...courseManagementRoute];

export const courseManagementState: Routes = [
    {
        path: '',
        children: COURSE_MANAGEMENT_ROUTES,
    },
];
