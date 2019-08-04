import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { tutorExerciseDashboardRoute } from './tutor-exercise-dashboard.route';
import { CourseComponent, CourseExerciseService, CourseScoreCalculationService, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';
import { RepositoryService } from 'app/entities/repository';
import { ArTEMiSResultModule, ResultComponent, ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { ArTEMiSTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ArTEMiSModelingEditorModule } from 'app/modeling-editor';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { ArTEMiSHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArTEMiSSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArTEMiSTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';

const ENTITY_STATES = [...tutorExerciseDashboardRoute];

@NgModule({
    imports: [
        BrowserModule,
        ArTEMiSSharedModule,
        ArTEMiSResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArTEMiSTutorCourseDashboardModule,
        ArTEMiSModelingEditorModule,
        AssessmentInstructionsModule,
        ArTEMiSHeaderExercisePageWithDetailsModule,
        ArTEMiSSidePanelModule,
        ArTEMiSTutorLeaderboardModule,
    ],
    declarations: [TutorExerciseDashboardComponent],
    exports: [ResultComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent, ResultComponent],
    providers: [
        CourseService,
        JhiAlertService,
        RepositoryService,
        ResultService,
        CourseExerciseService,
        ParticipationService,
        CourseScoreCalculationService,
        { provide: JhiLanguageService, useClass: JhiLanguageService },
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSTutorExerciseDashboardModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
