import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChartLine, faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-onboarding-assessment-ai',
    templateUrl: './onboarding-assessment-ai.component.html',
    imports: [FormsModule, TranslateDirective, FaIconComponent, DocumentationButtonComponent, NgClass],
})
export class OnboardingAssessmentAiComponent {
    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    protected readonly faChartLine = faChartLine;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    get complaintsEnabled(): boolean {
        const c = this.course();
        return (c.maxComplaintTimeDays ?? 0) > 0;
    }

    get requestMoreFeedbackEnabled(): boolean {
        return (this.course().maxRequestMoreFeedbackTimeDays ?? 0) > 0;
    }

    updateField(field: keyof Course, value: any) {
        const updated = { ...this.course(), [field]: value };
        this.courseUpdated.emit(updated);
    }

    toggleComplaints() {
        const updated = { ...this.course() };
        if (this.complaintsEnabled) {
            updated.maxComplaints = 0;
            updated.maxTeamComplaints = 0;
            updated.maxComplaintTimeDays = 0;
        } else {
            updated.maxComplaints = 3;
            updated.maxTeamComplaints = 3;
            updated.maxComplaintTimeDays = 7;
            updated.maxComplaintTextLimit = 2000;
            updated.maxComplaintResponseTextLimit = 2000;
        }
        this.courseUpdated.emit(updated);
    }

    toggleRequestMoreFeedback() {
        const updated = { ...this.course() };
        if (this.requestMoreFeedbackEnabled) {
            updated.maxRequestMoreFeedbackTimeDays = 0;
        } else {
            updated.maxRequestMoreFeedbackTimeDays = 7;
        }
        this.courseUpdated.emit(updated);
    }
}
