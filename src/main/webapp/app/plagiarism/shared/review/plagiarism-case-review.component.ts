import { Component, input, signal } from '@angular/core';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { Subject } from 'rxjs';
import { TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabPanelComponent, TumAetUiTabPanelsComponent, TumAetUiTabsComponent } from '@tumaet/ui-angular';
import { PlagiarismSplitViewComponent } from 'app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-plagiarism-case-review',
    templateUrl: './plagiarism-case-review.component.html',
    imports: [
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTabPanelsComponent,
        TumAetUiTabPanelComponent,
        PlagiarismSplitViewComponent,
        ArtemisTranslatePipe,
    ],
})
export class PlagiarismCaseReviewComponent {
    plagiarismCase = input.required<PlagiarismCase>();
    forStudent = input<boolean>(true);

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();

    /**
     * The comparison whose tab is shown.
     */
    readonly selectedComparison = signal<number | string | undefined>(0);

    /**
     * The comparisons that have been shown once. A comparison is only rendered (and its data loaded) when its tab is
     * first selected and is kept afterwards, so switching back preserves the selected file and scroll position.
     */
    readonly visitedComparisons = signal<ReadonlySet<number | string | undefined>>(new Set([0]));

    selectComparison(value: number | string | undefined): void {
        this.selectedComparison.set(value);
        this.visitedComparisons.update((visited) => new Set(visited).add(value));
    }
}
