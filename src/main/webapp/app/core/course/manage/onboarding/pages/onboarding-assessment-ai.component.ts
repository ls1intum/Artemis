import { Component, effect, input, output, signal } from '@angular/core';
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

    readonly complaintsToggled = signal(false);
    readonly requestMoreFeedbackToggled = signal(false);
    private initialized = false;

    constructor() {
        effect(() => {
            const c = this.course();
            if (!this.initialized) {
                this.complaintsToggled.set((c.maxComplaintTimeDays ?? 0) > 0);
                this.requestMoreFeedbackToggled.set((c.maxRequestMoreFeedbackTimeDays ?? 0) > 0);
                this.initialized = true;
            }
        });
    }

    updateField<K extends keyof Course>(field: K, value: Course[K]) {
        const current = Course.from(this.course());
        current[field] = value;
        this.courseUpdated.emit(current);
    }

    toggleComplaints() {
        const current = Course.from(this.course());
        if (this.complaintsToggled()) {
            this.complaintsToggled.set(false);
            current.maxComplaints = 0;
            current.maxTeamComplaints = 0;
            current.maxComplaintTimeDays = 0;
        } else {
            this.complaintsToggled.set(true);
            current.maxComplaints = 3;
            current.maxTeamComplaints = 3;
            current.maxComplaintTimeDays = 7;
            current.maxComplaintTextLimit = 2000;
            current.maxComplaintResponseTextLimit = 2000;
        }
        this.courseUpdated.emit(current);
    }

    toggleRequestMoreFeedback() {
        const current = Course.from(this.course());
        if (this.requestMoreFeedbackToggled()) {
            this.requestMoreFeedbackToggled.set(false);
            current.maxRequestMoreFeedbackTimeDays = 0;
        } else {
            this.requestMoreFeedbackToggled.set(true);
            current.maxRequestMoreFeedbackTimeDays = 7;
        }
        this.courseUpdated.emit(current);
    }
}
