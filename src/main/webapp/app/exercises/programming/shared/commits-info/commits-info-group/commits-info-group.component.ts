import { Component, Input } from '@angular/core';
import type { CommitInfo } from 'app/entities/programming/programming-submission.model';

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
    @Input() groupIndex: number;
    @Input() groupCount: number;
    @Input() pushNumber: number;
    @Input() localVC: boolean;
    @Input()
    set isGroupExpanded(value: boolean) {
        if (this.isExpanded !== value) {
            this.isExpanded = value;
        }
    }

    protected isExpanded = false;

    protected toggleExpand() {
        this.isExpanded = !this.isExpanded;
    }

    public getIsExpanded(): boolean {
        return this.isExpanded;
    }
}
