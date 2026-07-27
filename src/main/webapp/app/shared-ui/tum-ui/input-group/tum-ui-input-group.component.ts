import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Owned input group, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-inputgroup`: a horizontal flex row that lays out addon segments
 * ({@link TumUiInputGroupAddonComponent}) flush against a field (an `<input tumUiInput>` or a form-field
 * component). `align-items: stretch` makes the addons match the field's height, exactly like `p-inputgroup`.
 *
 * Unlike `p-inputgroup` (which is `width: 100%`), the group sets no width so the consumer controls it with a
 * utility (`w-full`, `w-auto`, …) — the audits filter uses `class="w-auto"` to size to content inside its
 * flex parent. Corner joining is owned by the addon (it rounds only its outer edge via `first:`/`last:`),
 * so addon+addon and addon+`tumUiInput` seams are exact; see the addon component for the field-seam note.
 */
@Component({
    selector: 'tum-ui-input-group',
    template: '<ng-content />',
    styleUrl: './tum-ui-input-group.component.scss',
    host: {
        class: 'tum-ui-input-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputGroupComponent {}
