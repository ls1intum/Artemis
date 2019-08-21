import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisQuizExerciseModule } from '../quiz-exercise/quiz-exercise.module';
import { ArtemisTextExerciseModule } from '../text-exercise/text-exercise.module';
import { ArtemisModelingExerciseModule } from '../modeling-exercise/modeling-exercise.module';
import { ArtemisFileUploadExerciseModule } from '../file-upload-exercise/file-upload-exercise.module';
import { ArtemisProgrammingExerciseModule } from '../programming-exercise/programming-exercise.module';

import {
    CourseComponent,
    CourseDeleteDialogComponent,
    CourseDeletePopupComponent,
    CourseDetailComponent,
    CourseExerciseService,
    coursePopupRoute,
    courseRoute,
    CourseService,
    CourseExercisesOverviewComponent,
    CourseUpdateComponent,
} from './';
import { CourseExerciseCardComponent } from 'app/entities/course/course-exercise-card.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisColorSelectorModule } from 'app/components/color-selector/color-selector.module';
import { ImageCropperModule } from 'ngx-image-cropper';
import { SortByModule } from 'app/components/pipes';
import { MomentModule } from 'ngx-moment';

const ENTITY_STATES = [...courseRoute, ...coursePopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisProgrammingExerciseModule,
        ArtemisFileUploadExerciseModule,
        ArtemisQuizExerciseModule,
        ArtemisTextExerciseModule,
        ArtemisModelingExerciseModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ReactiveFormsModule,
        ArtemisColorSelectorModule,
        ImageCropperModule,
        MomentModule,
    ],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDeleteDialogComponent,
        CourseUpdateComponent,
        CourseDeletePopupComponent,
        CourseExerciseCardComponent,
        CourseExercisesOverviewComponent,
    ],
    entryComponents: [CourseComponent, CourseUpdateComponent, CourseDeleteDialogComponent, CourseDeletePopupComponent, CourseExerciseCardComponent, CourseDeletePopupComponent],
    providers: [CourseService, CourseExerciseService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisCourseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
