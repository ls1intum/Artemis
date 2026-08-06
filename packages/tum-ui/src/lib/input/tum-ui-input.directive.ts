import { Directive, booleanAttribute, computed, input } from '@angular/core';
import { TumUiInputSize, tumUiInputClasses } from './tum-ui-input.variants';

@Directive({
    selector: 'input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumUiInputDirective {
    readonly tumUiInputSize = input<TumUiInputSize | undefined>(undefined);
    readonly tumUiInputInvalid = input(false, { transform: booleanAttribute });

    protected readonly hostClasses = computed(() => tumUiInputClasses({ size: this.tumUiInputSize(), invalid: this.tumUiInputInvalid() }));
}
