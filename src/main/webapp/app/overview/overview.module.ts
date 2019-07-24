import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArTEMiSStudentQuestionsModule } from 'app/student-questions/';

import {
    CourseExerciseDetailsComponent,
    CourseExerciseRowComponent,
    CourseExercisesComponent,
    CourseLectureDetailsComponent,
    CourseLecturesComponent,
    CourseOverviewComponent,
    CourseStatisticsComponent,
    ExerciseActionButtonComponent,
    ExerciseDetailsStudentActionsComponent,
    OVERVIEW_ROUTES,
    OverviewComponent,
    OverviewCourseCardComponent,
} from './';
import { ArTEMiSResultModule } from 'app/entities/result';
import { ArTEMiSSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArTEMiSHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { ArTEMiSCourseRegistrationSelector } from 'app/components/course-registration-selector/course-registration-selector.module';

const ENTITY_STATES = [...OVERVIEW_ROUTES];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArTEMiSResultModule,
        ArTEMiSProgrammingExerciseModule,
        ArTEMiSStudentQuestionsModule,
        ArTEMiSSidePanelModule,
        RouterModule.forChild(ENTITY_STATES),
        ArTEMiSHeaderExercisePageWithDetailsModule,
        ArTEMiSCourseRegistrationSelector,
    ],
    declarations: [
        OverviewComponent,
        CourseOverviewComponent,
        OverviewCourseCardComponent,
        CourseStatisticsComponent,
        CourseExerciseRowComponent,
        CourseExercisesComponent,
        CourseExerciseDetailsComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseLectureDetailsComponent,
        ExerciseActionButtonComponent,
        ExerciseDetailsStudentActionsComponent,
    ],
    entryComponents: [],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
    exports: [],
})
export class ArTEMiSOverviewModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
