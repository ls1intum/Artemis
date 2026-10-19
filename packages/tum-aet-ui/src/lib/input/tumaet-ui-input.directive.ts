import { Directive, ElementRef, booleanAttribute, computed, effect, inject, input } from '@angular/core';
import { TumAetUiInputSize, tumAetUiInputClasses } from './tumaet-ui-input.variants';
import { TUM_AET_UI_FORM_FIELD } from '../form-field/tumaet-ui-form-field.token';

let nextInputId = 0;

@Directive({
    selector: 'input[tumAetUiInput], textarea[tumAetUiInput], textarea[tumAetUiTextarea]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.id]': 'controlId()',
        '[attr.aria-describedby]': 'describedBy()',
        // Dropped rather than set to "false" while valid: the invalid border is a visual cue only, so a screen
        // reader must be told about the error state, but a valid field should carry no state attribute at all.
        '[attr.aria-invalid]': 'isInvalid() || null',
    },
})
export class TumAetUiInputDirective {
    private readonly elementRef = inject<ElementRef<HTMLInputElement | HTMLTextAreaElement>>(ElementRef);
    private readonly formField = inject(TUM_AET_UI_FORM_FIELD, { optional: true });

    readonly tumAetUiInputSize = input<TumAetUiInputSize | undefined>(undefined);
    readonly tumAetUiInputInvalid = input(false, { transform: booleanAttribute });

    /**
     * Overrides the element id. Set it from a wrapper that owns the id; a plain `id` attribute on the element
     * works just as well and is left untouched.
     */
    readonly tumAetUiInputId = input<string>();

    /** Extra description ids to merge in, for a wrapper component that owns describing text of its own. */
    readonly tumAetUiInputDescribedBy = input<string>();

    // Read before any binding is applied, so an id written as a static attribute keeps precedence over the id
    // an enclosing form field would otherwise hand down.
    private readonly staticId = this.elementRef.nativeElement.getAttribute('id');
    private readonly staticDescribedBy = this.elementRef.nativeElement.getAttribute('aria-describedby');
    private readonly fallbackId = `tumaet-ui-input-${nextInputId++}`;

    /** The id this element brought with it, if any, as opposed to one adopted from a form field. */
    private readonly ownId = computed(() => this.tumAetUiInputId() ?? this.staticId ?? undefined);

    /**
     * Resolved element id. A form field told to label a specific id wins, so the label can never point at an
     * element that is not there; otherwise an id the element brought wins, then the field's, then a generated
     * one.
     */
    readonly controlId = computed(() => this.formField?.explicitControlId() ?? this.ownId() ?? this.formField?.labelTargetId() ?? this.fallbackId);

    protected readonly describedBy = computed(() => {
        const ids = [this.tumAetUiInputDescribedBy(), this.staticDescribedBy, this.formField?.describedBy()].filter(Boolean);
        return ids.length ? ids.join(' ') : null;
    });

    protected readonly isInvalid = computed(() => this.tumAetUiInputInvalid() || (this.formField?.invalid() ?? false));

    protected readonly hostClasses = computed(() => tumAetUiInputClasses({ size: this.tumAetUiInputSize(), invalid: this.isInvalid() }));

    constructor() {
        // Tell the field which id it should label whenever this element brought one of its own.
        effect(() => {
            const ownId = this.ownId();
            if (ownId) {
                this.formField?.adoptControlId(ownId);
            }
        });
    }
}
