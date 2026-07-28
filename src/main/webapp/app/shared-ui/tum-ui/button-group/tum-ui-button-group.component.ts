import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Owned button group, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-buttongroup`: a presentational wrapper that welds its projected
 * `tum-ui-button` / native `<button>` children into a single segmented control, collapsing the shared border
 * between adjacent buttons and squaring off the inner corners (only the outer corners stay rounded). The
 * join rules reproduce the Aura `buttongroup` base style; they live in the stylesheet because they must
 * reach the bordered element inside each child (see the SCSS note).
 */
@Component({
    selector: 'tum-ui-button-group',
    template: '<ng-content />',
    styleUrl: './tum-ui-button-group.component.scss',
    host: {
        class: 'tum-ui-button-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonGroupComponent {}
