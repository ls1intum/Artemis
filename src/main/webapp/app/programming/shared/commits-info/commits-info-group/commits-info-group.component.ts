import { Component, Input } from '@angular/core';
import type { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { CommitsInfoRowComponent } from './commits-info-row/commits-info-row.component';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'jhi-commits-info-group',
    templateUrl: './commits-info-group.component.html',
    imports: [CommitsInfoRowComponent, NgStyle],
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
