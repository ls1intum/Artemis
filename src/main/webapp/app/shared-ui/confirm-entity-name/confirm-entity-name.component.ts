import { Component, OnDestroy, OnInit, effect, inject, input, signal, untracked } from '@angular/core';
import {
    ControlValueAccessor,
    FormBuilder,
    FormControl,
    FormsModule,
    NG_VALIDATORS,
    NG_VALUE_ACCESSOR,
    ReactiveFormsModule,
    ValidationErrors,
    Validator,
    Validators,
} from '@angular/forms';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CopyToClipboardButtonComponent } from 'app/shared-ui/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';

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
    imports: [NgClass, TranslateDirective, FormsModule, ReactiveFormsModule, CopyToClipboardButtonComponent],
})
export class ConfirmEntityNameComponent implements OnInit, OnDestroy, ControlValueAccessor, Validator {
    private fb = inject(FormBuilder);

    readonly warningTextColor = input<string>();
    readonly confirmationText = input.required<string>();
    readonly entityName = input<string>('');

    entityNameSignal = signal<string>('');

    control: FormControl<string>;

    onTouched = () => {};

    private onChangeSubs: Subscription[] = [];
    private onValidatorChange?: () => void;

    constructor() {
        // Mirror the entityName input into the display signal and notify the validator whenever it changes,
        // reproducing the original @Input setter side effects.
        effect(() => {
            const entityName = this.entityName();
            untracked(() => {
                this.entityNameSignal.set(entityName);
                this.onValidatorChange?.();
            });
        });
    }

    ngOnInit() {
        this.control = this.fb.control('', {
            nonNullable: true,
            validators: [Validators.required, this.compareWithEntityName.bind(this)],
        });
    }

    ngOnDestroy() {
        for (const sub of this.onChangeSubs) {
            sub.unsubscribe();
        }
    }

    writeValue(entityName: string | undefined | null) {
        if (typeof entityName === 'string') {
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

    registerOnValidatorChange(fn: () => void) {
        this.onValidatorChange = fn;
    }

    private compareWithEntityName(control: FormControl): ValidationErrors | null {
        if (control.value !== this.entityName()) {
            return { invalidName: true };
        }
        return null;
    }
}
