import { Directive, ElementRef, booleanAttribute, computed, inject, input } from '@angular/core';
import { TumUiInputSize, tumUiInputClasses } from './tum-ui-input.variants';
import { TUM_UI_FORM_FIELD } from '../form-field/tum-ui-form-field.token';

let nextInputId = 0;

@Directive({
    selector: 'input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.id]': 'controlId()',
        '[attr.aria-describedby]': 'describedBy()',
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

    /** Resolved element id: an explicit id first, then an enclosing form field's, then a generated one. */
    readonly controlId = computed(() => this.tumUiInputId() ?? this.staticId ?? this.formField?.labelTargetId() ?? this.fallbackId);

    protected readonly describedBy = computed(() => {
        const ids = [this.tumUiInputDescribedBy(), this.staticDescribedBy, this.formField?.describedBy()].filter(Boolean);
        return ids.length ? ids.join(' ') : null;
    });

    protected readonly isInvalid = computed(() => this.tumUiInputInvalid() || (this.formField?.invalid() ?? false));

    protected readonly hostClasses = computed(() => tumUiInputClasses({ size: this.tumUiInputSize(), invalid: this.isInvalid() }));
}
