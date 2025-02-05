import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    exports: [GradingInstructionsDetailsComponent],
    imports: [CommonModule, ArtemisSharedComponentModule, GradingInstructionsDetailsComponent],
})
export class StructuredGradingCriterionModule {}
