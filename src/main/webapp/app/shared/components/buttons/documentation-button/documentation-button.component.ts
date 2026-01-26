import { Component, inject, input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

// The routes here are used to build the link to the documentation.
// Therefore, it's important that they exactly match the url to the subpage of the documentation.
// Additionally, the case names must match the keys in documentationLinks.json for the tooltip.
const DocumentationLinks = {
    Course: 'instructor/courses',
    Lecture: 'instructor/lectures',
    Exercise: 'instructor/exercises/intro',
    Quiz: 'instructor/exercises/quiz-exercise',
    Model: 'instructor/exercises/modeling-exercise',
    Programming: 'instructor/exercises/programming-exercise',
    SshSetup: 'student/integrated-code-lifecycle',
    Text: 'instructor/exercises/textual-exercise',
    FileUpload: 'instructor/exercises/file-upload-exercise',
    Notifications: 'student/notifications',
    Competencies: 'instructor/adaptive-learning',
    StandardizedCompetencies: 'admin/adaptive-learning',
    GenerateCompetencies: 'instructor/adaptive-learning#generate-competencies',
    Communications: 'student/communication',
    Exams: 'instructor/exams/intro',
    PlagiarismChecks: 'instructor/plagiarism-check',
    Grading: 'instructor/grading',
    Units: 'instructor/lectures#lecture-units',
    Assessment: 'instructor/assessment',
    Statistics: 'instructor/learning-analytics',
    SuspiciousBehavior: 'instructor/exams/exam-timeline#33-suspicious-behavior-detection',
};

export type DocumentationType = keyof typeof DocumentationLinks;

@Component({
    selector: 'jhi-documentation-button',
    styleUrls: ['./documentation-button.component.scss'],
    template: `
        <a class="text-primary documentation-button ms-1" href="{{ BASE_URL + DocumentationLinks[this.type()] }}" target="_blank" rel="noopener noreferrer">
            <fa-icon [icon]="faCircleInfo" ngbTooltip="{{ getTooltipForType() }}" />
        </a>
    `,
    imports: [FaIconComponent, NgbTooltip],
})
export class DocumentationButtonComponent {
    private translateService = inject(TranslateService);

    readonly BASE_URL = 'https://docs.artemis.tum.de/';
    readonly faCircleInfo = faCircleInfo;
    readonly DocumentationLinks = DocumentationLinks;

    readonly type = input<DocumentationType | undefined>(undefined);

    getTooltipForType() {
        const typeKey = 'artemisApp.documentationLinks.' + this.type()?.toLowerCase();
        return this.translateService.instant('artemisApp.documentationLinks.prefix') + this.translateService.instant(typeKey);
    }
}
