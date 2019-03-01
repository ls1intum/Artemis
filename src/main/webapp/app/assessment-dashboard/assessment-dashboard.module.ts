import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { JhiLanguageHelper, UserRouteAccessService } from '../core';
import { HomeComponent } from '../home';
import { JhiMainComponent } from '../layouts';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SortByModule } from '../components/pipes/sort-by.module';
import { ArTEMiSResultModule, ResultComponent, ResultDetailComponent } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/exercise/:exerciseId/assessment',
        component: AssessmentDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), NgbModule, SortByModule, ArTEMiSResultModule],
    declarations: [AssessmentDashboardComponent],
    entryComponents: [HomeComponent, ResultComponent, ResultDetailComponent, AssessmentDashboardComponent, JhiMainComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }]
})
export class ArTEMiSAssessmentDashboardModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
