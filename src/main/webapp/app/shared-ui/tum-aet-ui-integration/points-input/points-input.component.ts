import { ChangeDetectionStrategy, Component, booleanAttribute, forwardRef, input, numberAttribute, signal } from '@angular/core';
import { AbstractControl, ControlValueAccessor, FormsModule, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { TumAetUiInputNumberComponent } from '@tumaet/ui-angular';

/** The number of fraction digits that every points input accepts; a longer fraction is cut off while typing. */
export const POINTS_MAX_FRACTION_DIGITS = 3;

/**
 * The input for points, bonus points and credits, so that all of them accept and show a number in the same way:
 * up to {@link POINTS_MAX_FRACTION_DIGITS} fraction digits, `.` and `,` as decimal separator in every browser
 * language, and no digit grouping. The bounds are applied when the field is left, like in `tumaet-ui-input-number`,
 * and reported as `min` and `max` errors of the form control as long as they are violated.
 */
@Component({
    selector: 'jhi-points-input',
    templateUrl: './points-input.component.html',
    styleUrl: './points-input.component.scss',
    imports: [FormsModule, TumAetUiInputNumberComponent],
    host: {
        '[class.jhi-points-input-fluid]': 'fluid()',
    },
    providers: [
        { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PointsInputComponent), multi: true },
        { provide: NG_VALIDATORS, useExisting: forwardRef(() => PointsInputComponent), multi: true },
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PointsInputComponent implements ControlValueAccessor, Validator {
    /** Lower bound applied when the field is left, and reported as `min` error while it is violated. */
    readonly min = input<number>();
    /** Upper bound applied when the field is left, and reported as `max` error while it is violated. */
    readonly max = input<number>();
    /** Increment of the arrow keys and of the step buttons. */
    readonly step = input(1, { transform: numberAttribute });
    readonly showButtons = input(false, { transform: booleanAttribute });
    readonly fluid = input(false, { transform: booleanAttribute });
    readonly invalid = input(false, { transform: booleanAttribute });
    readonly placeholder = input<string>();
    /** `id` of the inner `<input>`, so that an external `<label for>` and the end-to-end tests find it. */
    readonly inputId = input<string>();
    readonly name = input<string>();
    readonly ariaLabel = input<string>();
    readonly ariaLabelledBy = input<string>();
    readonly ariaDescribedBy = input<string>();

    protected readonly maxFractionDigits = POINTS_MAX_FRACTION_DIGITS;
    protected readonly acceptedDecimalSeparators = ['.', ','];
    protected readonly value = signal<number | undefined>(undefined);
    protected readonly isDisabled = signal(false);

    private onModelChange: (value: number | undefined) => void = () => {};
    private onModelTouched: () => void = () => {};

    protected onValueChange(value: number | undefined): void {
        this.value.set(value);
        this.onModelChange(value);
    }

    protected onFocusOut(): void {
        this.onModelTouched();
    }

    writeValue(value: number | undefined | null): void {
        this.value.set(value ?? undefined);
    }

    registerOnChange(fn: (value: number | undefined) => void): void {
        this.onModelChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onModelTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.isDisabled.set(isDisabled);
    }

    validate(control: AbstractControl): ValidationErrors | null {
        const actual = control.value;
        if (typeof actual !== 'number' || Number.isNaN(actual)) {
            return null;
        }
        const min = this.min();
        if (min !== undefined && actual < min) {
            return { min: { min, actual } };
        }
        const max = this.max();
        if (max !== undefined && actual > max) {
            return { max: { max, actual } };
        }
        return null;
    }
}
