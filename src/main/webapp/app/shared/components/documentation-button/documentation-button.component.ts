import { Component, Input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';

// The routes here are used to build the link to the documentation.
// Therefore, it's important that they exactly match the url to the subpage of the documentation.
// Additionally, the case names must match the keys in documentationLinks.json for the tooltip.
export enum DocumentationType {
    Course = <any>'courses/customizable/',
    Lecture = <any>'lectures/',
    Exercise = <any>'exercises/',
    Quiz = <any>'exercises/quiz/',
    Model = <any>'exercises/modeling/',
    Programming = <any>'exercises/programming/',
    Text = <any>'exercises/textual/',
    FileUpload = <any>'exercises/file-upload/',
    Notifications = <any>'notifications/',
    Competencies = <any>'learning-analytics/#id3',
    Communications = <any>'communication/',
    Exams = <any>'exam_mode/',
    PlagiarismChecks = <any>'plagiarism-check/',
    Grading = <any>'grading/',
    Units = <any>'lectures/#lecture-units',
    Assessment = <any>'exercises/assessment/',
    Statistics = <any>'learning-analytics/',
}

@Component({
    selector: 'jhi-documentation-button',
    styleUrls: ['./documentation-button.component.scss'],
    template: `
        <button type="button" class="text-primary documentation-button" (click)="openDocumentation()">
            <fa-icon [icon]="faCircleInfo" ngbTooltip="{{ getTooltipForType() | artemisTranslate }}"></fa-icon>
        </button>
    `,
})
export class DocumentationButtonComponent {
    baseUrl = 'https://ls1intum.github.io/Artemis/user/';

    @Input() type: DocumentationType;

    faCircleInfo = faCircleInfo;

    openDocumentation() {
        window.open(this.baseUrl + this.type, '_blank');
    }

    getTooltipForType() {
        return 'artemisApp.documentationLinks.' + DocumentationType[this.type].toLowerCase();
    }
}
