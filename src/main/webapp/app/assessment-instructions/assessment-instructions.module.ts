import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssessmentInstructionsComponent } from './assessment-instructions.component';
import { ExpandableParagraphComponent } from './expandable-paragraph/expandable-paragraph.component';

@NgModule({
    declarations: [AssessmentInstructionsComponent, ExpandableParagraphComponent],
    exports: [AssessmentInstructionsComponent],
    imports: [CommonModule],
})
export class AssessmentInstructionsModule {}
