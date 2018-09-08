import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { Course } from 'app/shared/model/course.model';
import { CourseService } from './course.service';
import { CourseComponent } from './course.component';
import { CourseDetailComponent } from './course-detail.component';
import { CourseUpdateComponent } from './course-update.component';
import { CourseDeletePopupComponent } from './course-delete-dialog.component';
import { ICourse } from 'app/shared/model/course.model';

@Injectable({ providedIn: 'root' })
export class CourseResolve implements Resolve<ICourse> {
    constructor(private service: CourseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((course: HttpResponse<Course>) => course.body));
        }
        return of(new Course());
    }
}

export const courseRoute: Routes = [
    {
        path: 'course',
        component: CourseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:id/view',
        component: CourseDetailComponent,
        resolve: {
            course: CourseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/new',
        component: CourseUpdateComponent,
        resolve: {
            course: CourseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:id/edit',
        component: CourseUpdateComponent,
        resolve: {
            course: CourseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const coursePopupRoute: Routes = [
    {
        path: 'course/:id/delete',
        component: CourseDeletePopupComponent,
        resolve: {
            course: CourseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
