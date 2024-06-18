import { Component, Input } from '@angular/core';
import type { CommitInfo } from 'app/entities/programming-submission.model';
import type { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-commits-info-group',
    templateUrl: './commits-info-group.component.html',
})
export class CommitsInfoGroupComponent {
    @Input() commits: CommitInfo[];
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() exerciseProjectKey?: string;
    @Input() isRepositoryView = false;
    @Input() user?: User;
    @Input() groupIndex: number;
    @Input() groupCount: number;
    @Input() pushNumber: number;

    protected isExpanded: boolean = false;

    protected toggleExpand() {
        this.isExpanded = !this.isExpanded;
    }

    public getIsExpanded(): boolean {
        return this.isExpanded;
    }
}
