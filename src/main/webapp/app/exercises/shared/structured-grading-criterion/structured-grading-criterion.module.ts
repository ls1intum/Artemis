import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';

@NgModule({
    exports: [GradingInstructionsDetailsComponent],
    imports: [CommonModule, GradingInstructionsDetailsComponent],
})
export class StructuredGradingCriterionModule {}
