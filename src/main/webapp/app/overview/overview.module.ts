import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'angular2-moment';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ExerciseTypePipe } from 'app/entities/exercise/';
import { SidePanelComponent } from 'app/components/side-panel/side-panel.component';

import {
    CourseExerciseDetailsComponent,
    CourseExerciseRowComponent,
    CourseExercisesComponent,
    CourseGradeBookComponent,
    CourseOverviewComponent,
    CourseStatisticsComponent,
    DifficultyBadgeComponent,
    ExerciseActionButtonComponent,
    ExerciseDetailsStudentActionsComponent,
    OVERVIEW_ROUTES,
    OverviewComponent,
    OverviewCourseCardComponent,
} from './';
import { ArTEMiSResultModule } from 'app/entities/result';

const ENTITY_STATES = [...OVERVIEW_ROUTES];

@NgModule({
    imports: [ArTEMiSSharedModule, ChartsModule, ClipboardModule, MomentModule, ArTEMiSResultModule, ArTEMiSProgrammingExerciseModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        OverviewComponent,
        CourseOverviewComponent,
        OverviewCourseCardComponent,
        CourseStatisticsComponent,
        CourseExerciseRowComponent,
        CourseExercisesComponent,
        CourseExerciseDetailsComponent,
        ExerciseActionButtonComponent,
        CourseGradeBookComponent,
        ExerciseDetailsStudentActionsComponent,
        DifficultyBadgeComponent,
        ExerciseTypePipe,
        SidePanelComponent,
    ],
    entryComponents: [],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
