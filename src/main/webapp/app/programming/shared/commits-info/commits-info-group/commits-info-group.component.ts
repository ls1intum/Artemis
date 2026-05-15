import { Component, Input, input } from '@angular/core';
import type { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { CommitsInfoRowComponent } from './commits-info-row/commits-info-row.component';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'jhi-commits-info-group',
    templateUrl: './commits-info-group.component.html',
    imports: [CommitsInfoRowComponent, NgStyle],
})
export class CommitsInfoGroupComponent {
    readonly commits = input<CommitInfo[]>(undefined!);
    readonly currentSubmissionHash = input<string>();
    readonly previousSubmissionHash = input<string>();
    readonly exerciseProjectKey = input<string>();
    readonly isRepositoryView = input(false);
    readonly groupIndex = input<number>(undefined!);
    readonly groupCount = input<number>(undefined!);
    readonly pushNumber = input<number>(undefined!);
    // TODO: Skipped for migration because:
    //  Accessor inputs cannot be migrated as they are too complex.
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
