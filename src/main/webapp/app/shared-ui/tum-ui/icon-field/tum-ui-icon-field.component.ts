import { ChangeDetectionStrategy, Component, ElementRef, Renderer2, afterNextRender, effect, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

/**
 * Owned icon-in-input wrapper, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-iconfield` + `p-inputicon`: wraps a projected `<input tumUiInput>`
 * (or `<textarea tumUiInput>`) and overlays a FontAwesome icon inside the field, on the leading (default) or
 * trailing side. Reproduces the Aura `iconfield` base CSS: the icon sits at `inset-inline-<side>: 0.75rem`
 * ({form.field.padding.x}), vertically centered, in the muted `{form.field.icon.color}`; the field gains
 * `2.5rem` of padding on the icon side (`2 * padding.x + icon.size = 2*0.75rem + 1rem`) so text never runs
 * under the icon.
 *
 * The icon-side padding is applied as an INLINE style on the projected field (Renderer2), not via a Tailwind
 * class: projected content lives in the consumer's view, so the wrapper's encapsulated CSS can't reach it,
 * and an inline style is the encapsulation-safe way to override the field's own `px-*` on exactly one side
 * (this is what `p-iconfield` effectively does with its `:not(:first-child)` padding rule).
 *
 * Usage (replacing the admin-sbom search field):
 *   <tum-ui-icon-field [icon]="faSearch">
 *       <input tumUiInput type="text" class="w-full" [ngModel]="..." (ngModelChange)="..." />
 *   </tum-ui-icon-field>
 */
export type TumUiIconFieldPosition = 'left' | 'right';

@Component({
    selector: 'tum-ui-icon-field',
    templateUrl: './tum-ui-icon-field.component.html',
    styleUrl: './tum-ui-icon-field.component.scss',
    imports: [FaIconComponent],
    host: {
        class: 'tum-ui-icon-field',
        '[attr.data-position]': 'iconPosition()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiIconFieldComponent {
    /** Leading/trailing FontAwesome icon. No icon (and no field padding) is applied unless this is set. */
    readonly icon = input<IconProp>();
    readonly iconPosition = input<TumUiIconFieldPosition>('left');

    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly renderer = inject(Renderer2);
    private field?: HTMLElement;

    constructor() {
        // Projected input isn't available until the view is rendered; grab it once, then apply padding.
        afterNextRender(() => {
            this.field = this.host.nativeElement.querySelector('input, textarea') ?? undefined;
            this.applyPadding();
        });
        // Re-apply when the icon or its side changes (the projected element itself is stable).
        effect(() => {
            this.icon();
            this.iconPosition();
            this.applyPadding();
        });
    }

    private applyPadding(): void {
        const field = this.field;
        if (!field) {
            return;
        }
        this.renderer.removeStyle(field, 'padding-inline-start');
        this.renderer.removeStyle(field, 'padding-inline-end');
        if (!this.icon()) {
            return;
        }
        const side = this.iconPosition() === 'right' ? 'padding-inline-end' : 'padding-inline-start';
        // 2 * {form.field.padding.x} + {icon.size} = 2 * 0.75rem + 1rem = 2.5rem (Aura iconfield rule).
        this.renderer.setStyle(field, side, '2.5rem');
    }
}
