import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExercisePreliminaryFeedbackOptionsComponent } from 'app/exercises/shared/preliminary-feedback/exercise-preliminary-feedback-options.component';

@NgModule({
    declarations: [ExercisePreliminaryFeedbackOptionsComponent],
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule],
    exports: [ExercisePreliminaryFeedbackOptionsComponent],
})
export class ExercisePreliminaryFeedbackOptionsModule {}
