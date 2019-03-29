import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssessmentInstructionsComponent } from './assessment-instructions.component';
import { ExpandableParagraphComponent } from './expandable-paragraph/expandable-paragraph.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

@NgModule({
    declarations: [AssessmentInstructionsComponent, ExpandableParagraphComponent],
    exports: [AssessmentInstructionsComponent],
    imports: [NgbModule, FontAwesomeModule, CommonModule],
})
export class AssessmentInstructionsModule {}
