import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faComments, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-onboarding-communication',
    templateUrl: './onboarding-communication.component.html',
    imports: [FormsModule, TranslateDirective, MarkdownEditorMonacoComponent, FaIconComponent, DocumentationButtonComponent, NgClass],
})
export class OnboardingCommunicationComponent {
    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    protected readonly CourseInformationSharingConfiguration = CourseInformationSharingConfiguration;
    protected readonly faComments = faComments;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    get communicationEnabled(): boolean {
        const config = this.course().courseInformationSharingConfiguration;
        return config === CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING || config === CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
    }

    get messagingEnabled(): boolean {
        return this.course().courseInformationSharingConfiguration === CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
    }

    toggleCommunication() {
        const updated = { ...this.course() };
        if (this.communicationEnabled) {
            updated.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        } else {
            updated.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        }
        this.courseUpdated.emit(updated);
    }

    toggleMessaging() {
        const updated = { ...this.course() };
        if (this.messagingEnabled) {
            updated.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        } else {
            updated.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        }
        this.courseUpdated.emit(updated);
    }

    updateCodeOfConduct(message: string) {
        const updated = { ...this.course(), courseInformationSharingMessagingCodeOfConduct: message };
        this.courseUpdated.emit(updated);
    }
}
