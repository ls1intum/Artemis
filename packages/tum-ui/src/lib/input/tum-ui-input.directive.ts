import { Directive, ElementRef, booleanAttribute, computed, effect, inject, input } from '@angular/core';
import { TumUiInputSize, tumUiInputClasses } from './tum-ui-input.variants';
import { TUM_UI_FORM_FIELD } from '../form-field/tum-ui-form-field.token';

let nextInputId = 0;

@Directive({
    selector: 'input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.id]': 'controlId()',
        '[attr.aria-describedby]': 'describedBy()',
        // Dropped rather than set to "false" while valid: the invalid border is a visual cue only, so a screen
        // reader must be told about the error state, but a valid field should carry no state attribute at all.
        '[attr.aria-invalid]': 'isInvalid() || null',
    },
})
export class TumUiInputDirective {
    private readonly elementRef = inject<ElementRef<HTMLInputElement | HTMLTextAreaElement>>(ElementRef);
    private readonly formField = inject(TUM_UI_FORM_FIELD, { optional: true });

    readonly tumUiInputSize = input<TumUiInputSize | undefined>(undefined);
    readonly tumUiInputInvalid = input(false, { transform: booleanAttribute });

    /**
     * Overrides the element id. Set it from a wrapper that owns the id; a plain `id` attribute on the element
     * works just as well and is left untouched.
     */
    readonly tumUiInputId = input<string>();

    /** Extra description ids to merge in, for a wrapper component that owns describing text of its own. */
    readonly tumUiInputDescribedBy = input<string>();

    // Read before any binding is applied, so an id written as a static attribute keeps precedence over the id
    // an enclosing form field would otherwise hand down.
    private readonly staticId = this.elementRef.nativeElement.getAttribute('id');
    private readonly staticDescribedBy = this.elementRef.nativeElement.getAttribute('aria-describedby');
    private readonly fallbackId = `tum-ui-input-${nextInputId++}`;

    /** The id this element brought with it, if any, as opposed to one adopted from a form field. */
    private readonly ownId = computed(() => this.tumUiInputId() ?? this.staticId ?? undefined);

    /**
     * Resolved element id. A form field told to label a specific id wins, so the label can never point at an
     * element that is not there; otherwise an id the element brought wins, then the field's, then a generated
     * one.
     */
    readonly controlId = computed(() => this.formField?.explicitControlId() ?? this.ownId() ?? this.formField?.labelTargetId() ?? this.fallbackId);

    protected readonly describedBy = computed(() => {
        const ids = [this.tumUiInputDescribedBy(), this.staticDescribedBy, this.formField?.describedBy()].filter(Boolean);
        return ids.length ? ids.join(' ') : null;
    });

    protected readonly isInvalid = computed(() => this.tumUiInputInvalid() || (this.formField?.invalid() ?? false));

    protected readonly hostClasses = computed(() => tumUiInputClasses({ size: this.tumUiInputSize(), invalid: this.isInvalid() }));

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
