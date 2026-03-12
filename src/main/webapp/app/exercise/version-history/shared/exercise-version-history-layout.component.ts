import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SplitterModule } from 'primeng/splitter';

/**
 * Resizable two-pane layout shell for the version history page.
 *
 * Uses a PrimeNG Splitter to divide the viewport into a left pane (timeline)
 * and a right pane (snapshot detail). Child content is projected via the
 * `[version-history-left]` and `[version-history-right]` content selectors.
 */
@Component({
    selector: 'jhi-exercise-version-history-layout',
    templateUrl: './exercise-version-history-layout.component.html',
    styleUrls: ['./exercise-version-history-layout.component.scss'],
    imports: [SplitterModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseVersionHistoryLayoutComponent {}
