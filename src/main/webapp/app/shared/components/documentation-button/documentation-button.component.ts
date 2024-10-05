import { Component, Input, inject } from '@angular/core';
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
    SshSetup: 'exercises/programming.html#repository-access',
    Text: 'exercises/textual/',
    FileUpload: 'exercises/file-upload/',
    Notifications: 'notifications/',
    Competencies: 'adaptive-learning/',
    StandardizedCompetencies: 'adaptive-learning/adaptive-learning-admin',
    GenerateCompetencies: 'adaptive-learning/adaptive-learning-instructor#generate-competencies',
    Communications: 'communication/',
    Exams: 'exam_mode/',
    PlagiarismChecks: 'plagiarism-check/',
    Grading: 'grading/',
    Units: 'lectures/#lecture-units',
    Assessment: 'assessment/',
    Statistics: 'learning-analytics/',
    SuspiciousBehavior: 'exams/instructors_guide#suspicious-behavior-detection',
};

export type DocumentationType = keyof typeof DocumentationLinks;

@Component({
    selector: 'jhi-documentation-button',
    styleUrls: ['./documentation-button.component.scss'],
    template: `
        <a class="text-primary documentation-button ms-1 mb-1" href="{{ BASE_URL + DocumentationLinks[this.type] }}">
            <fa-icon [icon]="faCircleInfo" ngbTooltip="{{ getTooltipForType() }}" />
        </a>
    `,
})
export class DocumentationButtonComponent {
    private translateService = inject(TranslateService);

    readonly BASE_URL = 'https://docs.artemis.cit.tum.de/user/';
    readonly faCircleInfo = faCircleInfo;
    readonly DocumentationLinks = DocumentationLinks;

    @Input() type: DocumentationType;

    getTooltipForType() {
        const typeKey = 'artemisApp.documentationLinks.' + this.type.toLowerCase();
        return this.translateService.instant('artemisApp.documentationLinks.prefix') + this.translateService.instant(typeKey);
    }
}
