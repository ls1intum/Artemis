import { Component, input, linkedSignal } from '@angular/core';
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

    // Local toggle state seeded from the parent's expand-all input. Re-seeds whenever the source
    // changes (idiomatic Angular 21 alternative to an input→signal copy-effect), and remains
    // independently writable via `.set()` / `.update()` for the per-row toggle.
    protected readonly isExpanded = linkedSignal(() => this.isGroupExpanded());

    protected toggleExpand() {
        this.isExpanded.update((expanded) => !expanded);
    }

    public getIsExpanded(): boolean {
        return this.isExpanded();
    }
}
