import { AfterViewInit, Component, ViewChild } from '@angular/core';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';

@Component({
    selector: 'jhi-orion-course-management-exercises',
    templateUrl: './orion-tutor-assessment.component.html',
})
export class OrionTutorAssessmentComponent implements AfterViewInit {
    @ViewChild(CodeEditorTutorAssessmentContainerComponent) container: CodeEditorTutorAssessmentContainerComponent;

    ngAfterViewInit() {
        console.log(this.container.referencedFeedback);
    }
}
