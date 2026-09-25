import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, signal } from '@angular/core';
import { TUM_AET_UI_FORM_FIELD, TumAetUiFormFieldContext } from './tumaet-ui-form-field.token';

let nextFormFieldId = 0;

/**
 * Labelled wrapper around a single form control: it owns the label, the required marker, and the hint and
 * error text, and wires `for`, `aria-describedby`, and the invalid state to the control it wraps.
 *
 * The control adopts the field's generated id, so a minimal field needs no ids at all:
 *
 * ```html
 * <tumaet-ui-form-field label="Login" required [invalid]="control.dirty && control.invalid">
 *     <input tumAetUiInput formControlName="login" />
 *     <ng-container tumAetUiFormFieldError>
 *         @if (control.errors?.required) { <span>Login is required</span> }
 *     </ng-container>
 * </tumaet-ui-form-field>
 * ```
 *
 * The error region is always rendered so its `role="alert"` announces when it appears; it stays hidden, and
 * out of the control's description, while `invalid` is false. An error replaces the hint as the description,
 * matching how a screen reader should report a field that has just failed validation.
 *
 * `tumaet-ui-date-picker` renders its own label and validation message, so it is already a complete field and
 * does not need this wrapper.
 */
@Component({
    selector: 'tumaet-ui-form-field',
    templateUrl: './tumaet-ui-form-field.component.html',
    styleUrl: './tumaet-ui-form-field.component.scss',
    host: {
        class: 'tumaet-ui-form-field',
    },
    providers: [{ provide: TUM_AET_UI_FORM_FIELD, useExisting: TumAetUiFormFieldComponent }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiFormFieldComponent implements TumAetUiFormFieldContext {
    /** Label text. Omit it when projecting a `[tumAetUiFormFieldLabel]` slot instead. */
    readonly label = input<string>('');

    /**
     * Id of the control this field labels. Defaults to a generated id, which a TUM AET UI control adopts unless
     * it carries an id of its own. Set it when the id has to be stable, such as for an end-to-end selector.
     */
    readonly controlId = input<string>();

    /**
     * Renders the required marker. The marker is decorative: the control still needs its own `required`, which
     * is what assistive technology reports.
     */
    readonly required = input(false, { transform: booleanAttribute });

    /** Helper text shown below the control while the field is valid. */
    readonly hint = input<string>();

    /** Shows the error region and marks the wrapped control invalid. */
    readonly invalid = input(false, { transform: booleanAttribute });

    /** Error text. Project a `[tumAetUiFormFieldError]` slot instead when several messages can apply. */
    readonly error = input<string>();

    /** Id a wrapped control reported because it brought one of its own. */
    private readonly reportedControlId = signal<string | undefined>(undefined);

    private readonly fieldId = nextFormFieldId++;
    private readonly generatedControlId = `tumaet-ui-form-field-${this.fieldId}-control`;
    protected readonly hintId = `tumaet-ui-form-field-${this.fieldId}-hint`;
    protected readonly errorId = `tumaet-ui-form-field-${this.fieldId}-error`;

    readonly explicitControlId = this.controlId;

    readonly labelTargetId = computed(() => this.controlId() ?? this.reportedControlId() ?? this.generatedControlId);

    adoptControlId(id: string): void {
        this.reportedControlId.set(id);
    }

    protected readonly showHint = computed(() => !!this.hint()?.trim() && !this.invalid());

    readonly describedBy = computed(() => {
        if (this.invalid()) {
            return this.errorId;
        }
        return this.showHint() ? this.hintId : undefined;
    });
}
