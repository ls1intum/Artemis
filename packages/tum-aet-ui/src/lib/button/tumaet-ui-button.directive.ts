import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Renderer2, booleanAttribute, computed, effect, inject, input } from '@angular/core';
import { TumAetUiButtonSeverity, TumAetUiButtonSize, TumAetUiButtonVariant, tumAetUiButtonClasses } from './tumaet-ui-button.variants';

@Component({
    selector: 'a[tumAetUiButton], button[tumAetUiButton]',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiButtonDirective {
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    private readonly renderer = inject(Renderer2);
    private readonly destroyRef = inject(DestroyRef);

    readonly disabled = input<boolean | undefined, unknown>(undefined, { transform: booleanAttribute });

    readonly severity = input<TumAetUiButtonSeverity>('primary');
    readonly size = input<TumAetUiButtonSize>('default');

    readonly variant = input<TumAetUiButtonVariant>('solid');

    protected readonly hostClasses = computed(
        () =>
            tumAetUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }) +
            (this.disabled() ? ' tumaet:opacity-60 tumaet:cursor-not-allowed' : ''),
    );

    constructor() {
        const tabIndex = this.element.getAttribute('tabindex');
        effect(() => {
            const disabled = this.disabled();
            if (disabled === undefined) return;
            if (this.element.tagName === 'BUTTON') {
                this.renderer.setProperty(this.element, 'disabled', disabled);
            } else {
                if (disabled) {
                    this.renderer.setAttribute(this.element, 'aria-disabled', 'true');
                    this.renderer.setAttribute(this.element, 'tabindex', '-1');
                } else {
                    this.renderer.removeAttribute(this.element, 'aria-disabled');
                    if (tabIndex === null) this.renderer.removeAttribute(this.element, 'tabindex');
                    else this.renderer.setAttribute(this.element, 'tabindex', tabIndex);
                }
            }
        });
        // Capture activation before RouterLink or application click handlers can navigate.
        const blockDisabledActivation = (event: Event) => {
            if (this.disabled()) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
        };
        for (const type of ['click', 'auxclick', 'contextmenu']) {
            this.element.addEventListener(type, blockDisabledActivation, true);
            this.destroyRef.onDestroy(() => this.element.removeEventListener(type, blockDisabledActivation, true));
        }
    }
}
