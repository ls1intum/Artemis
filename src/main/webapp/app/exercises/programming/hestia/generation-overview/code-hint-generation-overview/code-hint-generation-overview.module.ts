import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CodeHintGenerationStatusComponent } from './code-hint-generation-status/code-hint-generation-status.component';
import { SolutionEntryDetailsModalComponent } from './solution-entry-details-modal/solution-entry-details-modal.component';
import { ArtemisExerciseHintSharedModule } from 'app/exercises/shared/exercise-hint/shared/exercise-hint-shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisExerciseHintSharedModule, ArtemisSharedComponentModule],
    declarations: [CodeHintGenerationStatusComponent, SolutionEntryDetailsModalComponent],
    exports: [CodeHintGenerationStatusComponent],
})
export class ArtemisCodeHintGenerationOverviewModule {}
