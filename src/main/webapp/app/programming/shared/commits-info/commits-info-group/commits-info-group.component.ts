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
    readonly commits = input<CommitInfo[]>(undefined!);
    readonly currentSubmissionHash = input<string>();
    readonly previousSubmissionHash = input<string>();
    readonly exerciseProjectKey = input<string>();
    readonly isRepositoryView = input(false);
    readonly groupIndex = input<number>(undefined!);
    readonly groupCount = input<number>(undefined!);
    readonly pushNumber = input<number>(undefined!);
    readonly isGroupExpanded = input<boolean>(false);

    protected readonly isExpanded = signal(false);

    constructor() {
        // Mirror the legacy accessor setter behaviour: whenever the parent input changes,
        // align the local toggle state with it. Tracking only the input prevents the effect
        // from being re-triggered by local toggles.
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
