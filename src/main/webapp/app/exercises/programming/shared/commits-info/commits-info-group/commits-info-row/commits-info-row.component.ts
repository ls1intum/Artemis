import { Component, EventEmitter, Input, Output } from '@angular/core';
import type { CommitInfo } from 'app/entities/programming-submission.model';
import type { User } from 'app/core/user/user.model';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';

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
    @Input() user?: User;
    @Input() rowNumber: number;
    @Input() isExpanded: boolean;
    @Input() pushNumber: number;
    @Input() firstCommit: boolean;
    @Input() groupCommitCount: number;
    @Input() groupCommitIndex: number;
    @Output() toggleExpandEvent = new EventEmitter<void>();

    onToggleExpand() {
        this.toggleExpandEvent.emit();
    }

    localVC = false;
    faCircle = faCircle;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
}
