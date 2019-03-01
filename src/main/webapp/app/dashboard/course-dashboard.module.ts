import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { JhiLanguageHelper, UserRouteAccessService } from '../core';
import { HomeComponent } from '../home';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { CourseDashboardComponent } from './course-dashboard.component';
import { SortByModule } from '../components/pipes/sort-by.module';
import { JhiLanguageService } from 'ng-jhipster';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/dashboard',
        component: CourseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [ArTEMiSSharedModule, MomentModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [CourseDashboardComponent],
    entryComponents: [HomeComponent, CourseDashboardComponent, JhiMainComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }]
})
export class ArTEMiSInstructorCourseDashboardModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
