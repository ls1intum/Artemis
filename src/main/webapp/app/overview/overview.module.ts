import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisStudentQuestionsModule } from 'app/student-questions/';

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
    ProgrammingExerciseStudentIdeActionsComponent,
    OVERVIEW_ROUTES,
    OverviewComponent,
    OverviewCourseCardComponent,
} from './';
import { ArtemisResultModule } from 'app/entities/result';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { ArtemisCourseRegistrationSelector } from 'app/components/course-registration-selector/course-registration-selector.module';
import { IntellijModule } from 'app/intellij/intellij.module';

const ENTITY_STATES = [...OVERVIEW_ROUTES];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseModule,
        ArtemisStudentQuestionsModule,
        ArtemisSidePanelModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisCourseRegistrationSelector,
        IntellijModule,
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
        ProgrammingExerciseStudentIdeActionsComponent,
    ],
    entryComponents: [],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],

    exports: [ExerciseActionButtonComponent],
})
export class ArtemisOverviewModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
