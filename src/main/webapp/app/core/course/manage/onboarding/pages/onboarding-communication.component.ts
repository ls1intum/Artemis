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
    styleUrls: ['./_onboarding-pages.scss'],
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
        const current = Course.from(this.course());
        if (this.communicationEnabled) {
            current.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        } else {
            current.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        }
        this.courseUpdated.emit(current);
    }

    toggleMessaging() {
        const current = Course.from(this.course());
        if (this.messagingEnabled) {
            current.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        } else {
            current.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        }
        this.courseUpdated.emit(current);
    }

    updateCodeOfConduct(message: string) {
        const current = Course.from(this.course());
        current.courseInformationSharingMessagingCodeOfConduct = message;
        this.courseUpdated.emit(current);
    }
}
