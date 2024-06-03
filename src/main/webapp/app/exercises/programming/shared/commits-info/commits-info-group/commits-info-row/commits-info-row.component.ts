import { Component, Input } from '@angular/core';
import { CommitInfo } from 'app/entities/programming-submission.model';
import { User } from 'app/core/user/user.model';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-commits-info-row',
    templateUrl: './commits-info-row.component.html',
    styleUrls: ['./commits-info-row.component.scss'],
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
    @Input() toggleExpand: () => void;
    @Input() pushNumber: number;
    @Input() firstCommit: boolean;
    @Input() groupCommitCount: number;
    @Input() groupCommitIndex: number;

    localVC = false;
    faCircle = faCircle;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    getShortHash(hash?: string): string {
        return hash ? hash.slice(0, 7) : '';
    }
}
