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
    styleUrls: ['./_onboarding-pages.scss'],
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
        const current = this.course();
        (current as any)[field] = value;
        this.courseUpdated.emit(Course.from(current));
    }

    toggleComplaints() {
        const current = this.course();
        if (this.complaintsEnabled) {
            current.maxComplaints = 0;
            current.maxTeamComplaints = 0;
            current.maxComplaintTimeDays = 0;
        } else {
            current.maxComplaints = 3;
            current.maxTeamComplaints = 3;
            current.maxComplaintTimeDays = 7;
            current.maxComplaintTextLimit = 2000;
            current.maxComplaintResponseTextLimit = 2000;
        }
        this.courseUpdated.emit(Course.from(current));
    }

    toggleRequestMoreFeedback() {
        const current = this.course();
        if (this.requestMoreFeedbackEnabled) {
            current.maxRequestMoreFeedbackTimeDays = 0;
        } else {
            current.maxRequestMoreFeedbackTimeDays = 7;
        }
        this.courseUpdated.emit(Course.from(current));
    }
}
