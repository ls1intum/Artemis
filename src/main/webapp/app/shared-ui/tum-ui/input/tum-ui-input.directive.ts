import { Directive, booleanAttribute, computed, input } from '@angular/core';
import { TumUiInputSize, tumUiInputClasses } from 'app/shared-ui/tum-ui/input/tum-ui-input.variants';

/**
 * Owned text input / textarea styler, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `pInputText` (InputTextModule) and `pTextarea` (TextareaModule): like
 * them it is an ATTRIBUTE DIRECTIVE on a native `<input>` / `<textarea>` that ONLY applies styling. Because
 * it never touches the element's value or events, `[(ngModel)]`, `formControlName`, `type`, `placeholder`,
 * `pattern`, `maxlength`, `required`, `name`, `id` and any static `class` keep working untouched — the host
 * `[class]` binding merges with, and does not clobber, classes written in the template (e.g. `class="w-full"`).
 *
 * The class string is composed from {@link tumUiInputClasses} to match the Aura `inputtext` / `textarea`
 * look pixel-for-pixel (see that file for the token sources).
 *
 * Migration mapping:
 * - `pInputText`  -> `tumUiInput`
 * - `pTextarea`   -> `tumUiTextarea` (or `tumUiInput`; both selectors resolve to this directive on a textarea)
 * - `pSize="small|large"` -> `[tumUiInputSize]="'small'|'large'"` (aliased away from the native `size`
 *   attribute so `<input size="20">` still works)
 * - `[invalid]="expr"` -> `[tumUiInputInvalid]="expr"`
 */
@Directive({
    selector: 'input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumUiInputDirective {
    /** Mirrors PrimeNG `pSize`. Undefined = the normal size. Named distinctly from the native `size` attribute. */
    readonly tumUiInputSize = input<TumUiInputSize | undefined>(undefined);
    /** Mirrors PrimeNG `[invalid]`: paints the danger border (fixed, wins over hover/focus). */
    readonly tumUiInputInvalid = input(false, { transform: booleanAttribute });

    protected readonly hostClasses = computed(() => tumUiInputClasses({ size: this.tumUiInputSize(), invalid: this.tumUiInputInvalid() }));
}
