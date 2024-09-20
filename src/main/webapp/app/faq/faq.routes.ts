import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { FAQComponent } from 'app/faq/faq.component';
import { FAQService } from 'app/faq/faq.service';
import { FAQ } from 'app/entities/faq.model';
import { FAQUpdateComponent } from 'app/faq/faq-update.component';

@Injectable({ providedIn: 'root' })
export class FAQResolve implements Resolve<FAQ> {
    constructor(private faqService: FAQService) {}

    resolve(route: ActivatedRouteSnapshot): Observable<FAQ> {
        const faqId = route.params['faqId'];
        if (faqId) {
            return this.faqService.find(faqId).pipe(
                filter((response: HttpResponse<FAQ>) => response.ok),
                map((faq: HttpResponse<FAQ>) => faq.body!),
            );
        }
        return of(new FAQ());
    }
}

export const faqRoutes: Routes = [
    {
        path: ':courseId/faqs',
        component: CourseManagementTabBarComponent,
        children: [
            {
                path: '',
                component: FAQComponent,
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.faq.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                // Create a new path without a component defined to prevent the FAQ from being always rendered
                path: '',
                resolve: {
                    course: CourseManagementResolve,
                },
                children: [
                    {
                        path: 'new',
                        component: FAQUpdateComponent,
                        data: {
                            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'global.generic.create',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: ':faqId',
                        resolve: {
                            faq: FAQResolve,
                        },
                        children: [
                            {
                                path: 'edit',
                                component: FAQUpdateComponent,
                                data: {
                                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'global.generic.edit',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                        ],
                    },
                ],
            },
        ],
    },
];
