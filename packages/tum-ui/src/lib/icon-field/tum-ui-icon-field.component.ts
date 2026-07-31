import { ChangeDetectionStrategy, Component, ElementRef, Renderer2, computed, contentChild, effect, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

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
    readonly icon = input<IconProp>();
    readonly iconPosition = input<TumUiIconFieldPosition>('left');

    private readonly renderer = inject(Renderer2);

    private readonly field = contentChild(TumUiInputDirective, { read: ElementRef });

    private readonly iconSidePadding = computed<{ side: 'padding-inline-start' | 'padding-inline-end'; value: string } | undefined>(() =>
        this.icon() ? { side: this.iconPosition() === 'right' ? 'padding-inline-end' : 'padding-inline-start', value: 'calc(var(--tumaet-ui-spacing) * 10)' } : undefined,
    );

    constructor() {
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
