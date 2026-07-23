import { ChangeDetectionStrategy, Component, ElementRef, Renderer2, computed, contentChild, effect, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';

/**
 * Icon-in-input wrapper, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Wraps a projected `<input tumUiInput>` (or `<textarea tumUiInput>`) and overlays a FontAwesome icon inside the
 * field, on the leading (default) or trailing side. The icon sits at `inset-inline-<side>: 0.75rem`, vertically
 * centered, in the muted icon color; the field gains matching padding on the icon side so text never runs under it.
 *
 * Usage:
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

    private readonly renderer = inject(Renderer2);

    /** The projected field. A content query, so it tracks the projected `tumUiInput` element reactively. */
    private readonly field = contentChild(TumUiInputDirective, { read: ElementRef });

    /**
     * Which side to reserve room on, and how much, so text clears the icon: `2 * field padding.x + icon size`
     * (`2 * 0.75rem + 1rem = 2.5rem`). `undefined` while no icon is set, so no padding is applied.
     */
    private readonly iconSidePadding = computed<{ side: 'padding-inline-start' | 'padding-inline-end'; value: string } | undefined>(() =>
        this.icon() ? { side: this.iconPosition() === 'right' ? 'padding-inline-end' : 'padding-inline-start', value: '2.5rem' } : undefined,
    );

    constructor() {
        // Projected content lives in the consumer's view, so the wrapper's encapsulated CSS and template style
        // bindings can't reach it — the padding has to be written onto the element imperatively. Driven by the
        // computed above, so it re-runs whenever the icon, its side, or the projected field changes.
        effect(() => {
            const field = this.field()?.nativeElement;
            if (!field) {
                return;
            }
            const padding = this.iconSidePadding();
            this.renderer.removeStyle(field, 'padding-inline-start');
            this.renderer.removeStyle(field, 'padding-inline-end');
            if (padding) {
                this.renderer.setStyle(field, padding.side, padding.value);
            }
        });
    }
}
