import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Result } from 'app/entities/result';

/**
 * The <jhi-assessment-header> component is shared between the modeling and text assessment interfaces.
 * It displays a header bar above the assessment editor with information of locking, as well as offering save/submit/etc buttons.
 * This guarantees a unified look and feel for both interfaces.
 * Depending Components need to perform actions based on the save/submit/cancel/resolveConflict/nextSubmission/goBack outputs.
 */
@Component({
    selector: 'jhi-assessment-header',
    templateUrl: './assessment-header.component.html',
    styleUrls: ['./assessment-header.component.scss'],
})
export class AssessmentHeaderComponent {
    @Input() showBackButton: boolean;
    @Output() goBack = new EventEmitter<void>();

    @Input() isLoading: boolean;
    @Input() busy: boolean;

    @Input() isAssessor: boolean;
    @Input() isAtLeastInstructor: boolean;
    @Input() canOverride: boolean;

    @Input() result: Result | null;
    @Input() hasConflict = false;
    @Input() hasComplaint = false;
    @Input() assessmentsAreValid: boolean;

    @Output() save = new EventEmitter<void>();
    @Output() submit = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
    @Output() resolveConflict = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
}
