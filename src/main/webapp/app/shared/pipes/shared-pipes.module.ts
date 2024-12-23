import { NgModule } from '@angular/core';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { TypeCheckPipe } from 'app/shared/pipes/type-check.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';
import { ExerciseTypePipe } from 'app/shared/pipes/exercise-type.pipe';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NegatedTypeCheckPipe } from 'app/shared/pipes/negated-type-check.pipe';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { AsPipe } from 'app/shared/pipes/as.pipe';

@NgModule({
    declarations: [
        SafeUrlPipe,
        SafeResourceUrlPipe,
        RemoveKeysPipe,
        ExerciseCourseTitlePipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        NegatedTypeCheckPipe,
        TruncatePipe,
        SearchFilterPipe,
        AsPipe,
    ],
    exports: [
        SafeUrlPipe,
        RemoveKeysPipe,
        ExerciseCourseTitlePipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        NegatedTypeCheckPipe,
        TruncatePipe,
        SafeResourceUrlPipe,
        SearchFilterPipe,
        AsPipe,
    ],
    providers: [SafeResourceUrlPipe, ArtemisTranslatePipe],
})
export class ArtemisSharedPipesModule {}
