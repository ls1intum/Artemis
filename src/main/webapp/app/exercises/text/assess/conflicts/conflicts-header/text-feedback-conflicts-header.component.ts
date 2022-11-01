import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faArrowLeft, faArrowRight, faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-text-feedback-conflicts-header',
    templateUrl: './text-feedback-conflicts-header.component.html',
    styleUrls: ['./text-feedback-conflicts-header.component.scss'],
})
export class TextFeedbackConflictsHeaderComponent {
    @Input() numberOfConflicts: number;
    @Input() haveRights: boolean;
    @Input() overrideBusy: boolean;
    @Input() markBusy: boolean;
    @Input() isOverrideDisabled: boolean;
    @Input() isMarkingDisabled: boolean;
    @Output() didChangeConflictIndex = new EventEmitter<number>();
    @Output() overrideLeftSubmission = new EventEmitter<void>();
    @Output() discardConflict = new EventEmitter<void>();

    currentConflictIndex = 1;

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    onNextConflict() {
        if (this.currentConflictIndex < this.numberOfConflicts) {
            this.currentConflictIndex++;
            this.didChangeConflictIndex.emit(this.currentConflictIndex);
        }
    }

    onPrevConflict() {
        if (this.currentConflictIndex > 1) {
            this.currentConflictIndex--;
            this.didChangeConflictIndex.emit(this.currentConflictIndex);
        }
    }
}
