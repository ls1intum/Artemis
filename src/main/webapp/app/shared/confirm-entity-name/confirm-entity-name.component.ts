import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-confirm-entity-name',
    templateUrl: './confirm-entity-name.component.html',
    styleUrls: ['./confirm-entity-name.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: ConfirmEntityNameComponent,
        },
        {
            provide: NG_VALIDATORS,
            multi: true,
            useExisting: ConfirmEntityNameComponent,
        },
    ],
})
export class ConfirmEntityNameComponent implements OnInit, OnDestroy, ControlValueAccessor, Validator {
    @Input() warningTextColor: string;
    @Input() confirmationText: string;
    @Input() entityName: string;

    control: FormControl<string>;

    constructor(private fb: FormBuilder) {}

    onTouched = () => {};

    private onChangeSubs: Subscription[] = [];

    ngOnInit() {
        this.control = this.fb.control('', {
            nonNullable: true,
            validators: [Validators.required, Validators.pattern(this.entityName)],
        });
    }

    ngOnDestroy() {
        for (const sub of this.onChangeSubs) {
            sub.unsubscribe();
        }
    }

    writeValue(entityName: string) {
        if (entityName) {
            this.control.setValue(entityName, { emitEvent: false });
        }
    }

    registerOnChange(onChange: (_: string) => void) {
        this.onChangeSubs.push(this.control.valueChanges.subscribe(onChange));
    }

    registerOnTouched(fn: () => void) {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        if (isDisabled) {
            this.control.disable();
        } else {
            this.control.enable();
        }
    }

    validate(): ValidationErrors | null {
        if (this.control.valid) {
            return null;
        }

        return this.control.errors;
    }
}
