import { Directive, booleanAttribute, input } from '@angular/core';

/** Common option and value semantics for single-value and multi-value listbox controls. */
@Directive()
export abstract class OptionControl {
    readonly options = input<readonly unknown[]>([]);

    /** Property name used as the visible label for object options. */
    readonly optionLabel = input<string>();

    /** Property name written to the form value; omit it to write the option itself. */
    readonly optionValue = input<string>();

    readonly placeholder = input<string>();
    readonly disabled = input(false, { transform: booleanAttribute });

    /** Reports the control as required to assistive technology. Validation stays with the form control. */
    readonly required = input(false, { transform: booleanAttribute });

    protected label(option: unknown): string {
        return this.toText(this.propertyValue(option, this.optionLabel()));
    }

    protected resolveValue(option: unknown): unknown {
        return this.propertyValue(option, this.optionValue());
    }

    protected toText(value: unknown): string {
        switch (typeof value) {
            case 'string':
                return value;
            case 'number':
            case 'boolean':
            case 'bigint':
                return String(value);
            default:
                return '';
        }
    }

    private propertyValue(option: unknown, key: string | undefined): unknown {
        return key && option !== null && typeof option === 'object' ? (option as Record<string, unknown>)[key] : option;
    }
}
