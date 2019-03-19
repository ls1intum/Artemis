import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { UserRouteAccessService } from '../core';
import { HomeComponent } from '../home';
import { JhiMainComponent } from '../layouts';
import { ModelingStatisticsComponent } from './modeling-statistics.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/exercise/:exerciseId/statistics',
        component: ModelingStatisticsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), NgbModule],
    declarations: [ModelingStatisticsComponent],
    entryComponents: [HomeComponent, ModelingStatisticsComponent, JhiMainComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }]
})
export class ArTEMiSModelingStatisticsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
