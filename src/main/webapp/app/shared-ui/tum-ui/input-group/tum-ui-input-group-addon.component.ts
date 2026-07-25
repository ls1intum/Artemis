import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Owned input-group addon, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-inputgroup-addon`: the fixed label/icon segment of an
 * {@link TumUiInputGroupComponent}. Renders projected content (or a `jhiTranslate`-populated label, as the
 * audits filter uses: `<tum-ui-input-group-addon jhiTranslate="audits.filter.from" />`).
 *
 * Colors + borders come from sanctioned surface tokens on the host (see the `host.class` below), reproducing
 * the Aura `inputgroup.addon` tokens: background {form.field.background}, border {form.field.border.color},
 * min-width 2.5rem, padding 0.5rem. It always draws a top+bottom border and rounds/borders only its OUTER
 * edge (`first:` = inline-start, `last:` = inline-end, logical for RTL), so a middle addon is square on both
 * inline edges — exactly PrimeNG's addon border/radius rules, minus the `--p-*` primitives.
 *
 * Text color uses `text-muted-color` (the semantic muted token, {surface.500}/{surface.400}) rather than the
 * exact Aura addon color {surface.400}; a near-identical, deliberate house-style choice (see the return note).
 *
 * Field-seam note: PrimeNG also squares the ADJACENT field's inner corners via `.p-inputgroup > .p-component`.
 * That reaches into the field's own styles, which encapsulation forbids for projected content, so a field
 * keeps its own rounding at the seam. This is exact for `tumUiInput` fields once they drop their radius in a
 * group (future) and a small cosmetic seam for the not-yet-migrated `jhi-date-time-picker` in audits.
 */
@Component({
    selector: 'tum-ui-input-group-addon',
    template: '<ng-content />',
    styleUrl: './tum-ui-input-group-addon.component.scss',
    host: {
        class:
            'tum-ui-input-group-addon bg-surface-0 text-muted-color border-y border-surface-300 ' +
            'first:border-s first:rounded-s-md last:border-e last:rounded-e-md ' +
            'dark:bg-surface-950 dark:border-surface-600',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputGroupAddonComponent {}
