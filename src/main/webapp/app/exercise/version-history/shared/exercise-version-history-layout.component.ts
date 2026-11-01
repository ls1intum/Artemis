import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TumAetUiSplitterComponent } from '@tumaet/ui-angular';

/**
 * Resizable two-pane layout shell for the version history page.
 *
 * Uses a TUM UI splitter to divide the viewport into a left pane (timeline)
 * and a right pane (snapshot detail). Child content is projected via the
 * `[version-history-left]` and `[version-history-right]` content selectors.
 */
@Component({
    selector: 'jhi-exercise-version-history-layout',
    templateUrl: './exercise-version-history-layout.component.html',
    styleUrls: ['./exercise-version-history-layout.component.scss'],
    imports: [TumAetUiSplitterComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseVersionHistoryLayoutComponent {
    readonly isNarrow = toSignal(
        inject(BreakpointObserver)
            .observe('(max-width: 64rem)')
            .pipe(map((state) => state.matches)),
        { initialValue: false },
    );
}
