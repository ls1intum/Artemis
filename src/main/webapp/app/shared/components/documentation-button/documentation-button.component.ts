import { Component, Input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';

// The routes here are used to build the link to the documentation.
// Therefore, it's important that they exactly match the url to the subpage of the documentation.
// Additionally, the case names must match the keys in documentationLinks.json for the tooltip.
const DocumentationLinks = {
    Course: 'courses/customizable/',
    Lecture: 'lectures/',
    Exercise: 'exercises/',
    Quiz: 'exercises/quiz/',
    Model: 'exercises/modeling/',
    Programming: 'exercises/programming/',
    Text: 'exercises/textual/',
    FileUpload: 'exercises/file-upload/',
    Notifications: 'notifications/',
    Competencies: 'learning-analytics/#id3',
    Communications: 'communication/',
    Exams: 'exam_mode/',
    PlagiarismChecks: 'plagiarism-check/',
    Grading: 'grading/',
    Units: 'lectures/#lecture-units',
    Assessment: 'assessment/',
    Statistics: 'learning-analytics/',
    SuspiciousBehavior: 'exams/instructors_guide.html#suspicious-behavior-detection',
};

export type DocumentationType = keyof typeof DocumentationLinks;

const baseUrl = 'https://docs.artemis.cit.tum.de/user/';

@Component({
    selector: 'jhi-documentation-button',
    styleUrls: ['./documentation-button.component.scss'],
    template: `
        <button type="button" class="text-primary documentation-button" (click)="openDocumentation()">
            <fa-icon [icon]="faCircleInfo" ngbTooltip="{{ getTooltipForType() }}"></fa-icon>
        </button>
    `,
})
export class DocumentationButtonComponent {
    @Input() type: DocumentationType;

    readonly faCircleInfo = faCircleInfo;

    constructor(private translateService: TranslateService) {}

    openDocumentation() {
        window.open(baseUrl + DocumentationLinks[this.type].toLowerCase(), '_blank');
    }

    getTooltipForType() {
        const typeKey = 'artemisApp.documentationLinks.' + this.type.toLowerCase();
        return this.translateService.instant('artemisApp.documentationLinks.prefix') + this.translateService.instant(typeKey);
    }
}
