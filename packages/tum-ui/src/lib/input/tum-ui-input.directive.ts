import { Directive, booleanAttribute, computed, input } from '@angular/core';
import { TumUiInputSize, tumUiInputClasses } from './tum-ui-input.variants';

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
        '[attr.aria-invalid]': "isInvalid() ? 'true' : null",
        '[attr.data-slot]': '"input"',
        '[attr.data-invalid]': 'isInvalid() || null',
    },
})
export class TumUiInputDirective {
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

    /* eslint-disable @typescript-eslint/no-deprecated -- this is the shim that keeps the deprecated inputs working. */
    protected readonly effectiveSize = computed(() => this.size() ?? this.tumUiInputSize());
    protected readonly isInvalid = computed(() => this.invalid() || this.tumUiInputInvalid());
    /* eslint-enable @typescript-eslint/no-deprecated */
    protected readonly hostClasses = computed(() => tumUiInputClasses({ size: this.effectiveSize(), invalid: this.isInvalid() }));
}
