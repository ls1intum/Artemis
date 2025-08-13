import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faExclamationTriangle, faRedo } from '@fortawesome/free-solid-svg-icons';

export type FeedbackIconType = 'retry' | 'success' | 'error';

@Component({
    selector: 'jhi-unified-feedback',
    templateUrl: './unified-feedback.component.html',
    styleUrls: ['./unified-feedback.component.scss'],
    imports: [NgClass, FaIconComponent],
})
export class UnifiedFeedbackComponent {
    feedbackContent = input<string>('');
    points = input<number>(0);
    icon = input<FeedbackIconType>('success');
    title = input<string | undefined>(undefined);
    reference = input<string | undefined>(undefined);

    // Icons
    readonly faCheck = faCheck;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faRedo = faRedo;

    getIconForType(): any {
        switch (this.icon()) {
            case 'success':
                return this.faCheck;
            case 'error':
                return this.faExclamationTriangle;
            case 'retry':
                return this.faRedo;
            default:
                return this.faCheck;
        }
    }

    getAlertClass(): string {
        switch (this.icon()) {
            case 'success':
                return 'alert-success';
            case 'error':
                return 'alert-danger';
            case 'retry':
                return 'alert-warning';
            default:
                return 'alert-info';
        }
    }
}
