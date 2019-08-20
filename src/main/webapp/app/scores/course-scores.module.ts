import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../shared';
import { JhiLanguageHelper, UserRouteAccessService } from '../core';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { CourseScoresComponent } from './course-scores.component';
import { SortByModule } from 'app/components/pipes';
import { JhiLanguageService } from 'ng-jhipster';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/dashboard',
        component: CourseScoresComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [CourseScoresComponent],
    entryComponents: [HomeComponent, CourseScoresComponent, JhiMainComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisCourseScoresModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
