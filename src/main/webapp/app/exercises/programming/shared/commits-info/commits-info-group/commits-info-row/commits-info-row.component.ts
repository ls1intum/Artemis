import { Component, EventEmitter, Input, Output } from '@angular/core';
import type { CommitInfo } from 'app/entities/programming/programming-submission.model';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { faAngleDown, faAngleLeft } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-commits-info-row',
    templateUrl: './commits-info-row.component.html',
})
export class CommitsInfoRowComponent {
    @Input() commit: CommitInfo;
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() exerciseProjectKey?: string;
    @Input() isRepositoryView = false;
    @Input() rowNumber: number;
    @Input() isExpanded: boolean;
    @Input() pushNumber: number;
    @Input() firstCommit: boolean;
    @Input() groupCommitCount: number;
    @Input() groupCommitIndex: number;
    @Input() localVC: boolean;
    @Output() toggleExpandEvent = new EventEmitter<void>();

    onToggleExpand() {
        this.toggleExpandEvent.emit();
    }

    readonly faCircle = faCircle;
    readonly faAngleLeft = faAngleLeft;
    readonly faAngleDown = faAngleDown;
}
