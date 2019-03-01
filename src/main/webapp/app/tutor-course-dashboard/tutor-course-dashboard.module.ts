import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { tutorCourseDashboardRoute } from './tutor-course-dashboard.route';
import { CourseComponent, CourseExerciseService, CourseScoreCalculationService, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { RepositoryService } from 'app/entities/repository';
import { ArTEMiSResultModule, ResultComponent, ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { TutorParticipationGraphComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';

const ENTITY_STATES = [...tutorCourseDashboardRoute];

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, ArTEMiSResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [TutorCourseDashboardComponent, TutorParticipationGraphComponent],
    exports: [TutorParticipationGraphComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent, ResultComponent],
    providers: [
        CourseService,
        JhiAlertService,
        RepositoryService,
        ResultService,
        CourseExerciseService,
        ParticipationService,
        CourseScoreCalculationService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSTutorCourseDashboardModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
