import { Directive, ElementRef, booleanAttribute, computed, effect, inject, input } from '@angular/core';
import { TumUiInputSize, tumUiInputClasses } from './tum-ui-input.variants';
import { TUM_UI_FORM_FIELD } from '../form-field/tum-ui-form-field.token';

let nextInputId = 0;

/**
 * Styles a native `<input>` or `<textarea>` as a design-system field, and keeps its invalid state honest.
 *
 * The `aria-invalid` binding is the point. A red border with no `aria-invalid` conveys the failure by colour
 * alone, and every consumer that noticed had to hand-add the attribute next to the input that already knew.
 *
 * The inputs are `size` and `invalid`, unprefixed: the selector already scopes them to an element carrying
 * `tumUiInput`, and the prefixed spellings made one form read `[tumUiInputInvalid]` on a text field and
 * `[invalid]` on the number field beside it.
 */
@Directive({
    selector: 'input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.id]': 'controlId()',
        '[attr.aria-describedby]': 'describedBy()',
        '[attr.aria-invalid]': "isInvalid() ? 'true' : null",
        '[attr.data-slot]': '"input"',
        '[attr.data-invalid]': 'isInvalid() || null',
    },
})
export class TumUiInputDirective {
    private readonly elementRef = inject<ElementRef<HTMLInputElement | HTMLTextAreaElement>>(ElementRef);
    private readonly formField = inject(TUM_UI_FORM_FIELD, { optional: true });
    /**
     * Size step.
     *
     * `<input size>` is also a native attribute. It is not a hazard in practice, because the union rejects a
     * numeric string and Angular's template type checker reports it at build time rather than at runtime — but
     * where a native character width is genuinely wanted, set it with `[attr.size]`.
     */
    readonly size = input<TumUiInputSize | undefined>(undefined);

    /** Marks the field invalid: a danger border **and** `aria-invalid`, so the state is never colour-only. */
    readonly invalid = input(false, { transform: booleanAttribute });

    /** @deprecated Use `size`. The selector already scopes the input, so the prefix says nothing. */
    readonly tumUiInputSize = input<TumUiInputSize | undefined>(undefined);

    /** @deprecated Use `invalid`. */
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

    /* eslint-disable @typescript-eslint/no-deprecated -- this is the shim that keeps the deprecated inputs working. */
    protected readonly effectiveSize = computed(() => this.size() ?? this.tumUiInputSize());
    protected readonly isInvalid = computed(() => this.invalid() || this.tumUiInputInvalid() || (this.formField?.invalid() ?? false));
    /* eslint-enable @typescript-eslint/no-deprecated */
    protected readonly hostClasses = computed(() => tumUiInputClasses({ size: this.effectiveSize(), invalid: this.isInvalid() }));

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
