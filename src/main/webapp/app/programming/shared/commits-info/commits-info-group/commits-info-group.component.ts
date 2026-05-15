import { Component, effect, input, signal } from '@angular/core';
import type { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { CommitsInfoRowComponent } from './commits-info-row/commits-info-row.component';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'jhi-commits-info-group',
    templateUrl: './commits-info-group.component.html',
    imports: [CommitsInfoRowComponent, NgStyle],
})
export class CommitsInfoGroupComponent {
    readonly commits = input.required<CommitInfo[]>();
    readonly currentSubmissionHash = input<string>();
    readonly previousSubmissionHash = input<string>();
    readonly exerciseProjectKey = input<string>();
    readonly isRepositoryView = input(false);
    readonly groupIndex = input.required<number>();
    readonly groupCount = input.required<number>();
    readonly pushNumber = input.required<number>();
    readonly isGroupExpanded = input<boolean>(false);

    protected readonly isExpanded = signal(false);

    constructor() {
        // Reset local toggle state whenever the parent's expand-all state changes.
        effect(() => {
            const value = this.isGroupExpanded();
            this.isExpanded.set(value);
        });
    }

    protected toggleExpand() {
        this.isExpanded.update((expanded) => !expanded);
    }

    public getIsExpanded(): boolean {
        return this.isExpanded();
    }
}
