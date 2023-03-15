import { NgModule } from '@angular/core';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AverageByPipe } from 'app/shared/pipes/average-by.pipe';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { ExerciseTypePipe } from 'app/shared/pipes/exercise-type.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { NegatedTypeCheckPipe } from 'app/shared/pipes/negated-type-check.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { SanitizeHtmlPipe } from 'app/shared/pipes/sanitize-html.pipe';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';
import { TypeCheckPipe } from 'app/shared/pipes/type-check.pipe';

@NgModule({
    declarations: [
        SafeHtmlPipe,
        SafeUrlPipe,
        SafeResourceUrlPipe,
        RemoveKeysPipe,
        ExerciseCourseTitlePipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        NegatedTypeCheckPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
        AverageByPipe,
        GradeStepBoundsPipe,
    ],
    exports: [
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        ExerciseCourseTitlePipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        NegatedTypeCheckPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
        SafeResourceUrlPipe,
        AverageByPipe,
        GradeStepBoundsPipe,
    ],
    providers: [SafeResourceUrlPipe, ArtemisTranslatePipe],
})
export class ArtemisSharedPipesModule {}
