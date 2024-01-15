import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormControl, FormRecord, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validators } from '@angular/forms';
import { Subscription, concatMap } from 'rxjs';

import type { MathTaskExpressionInput, MathTaskExpressionValue } from './expression';
import { MathTaskEditorInputType } from './types';
import { filter, map } from 'rxjs/operators';

type MathTaskInput = MathTaskExpressionInput;

@Component({
    selector: 'jhi-math-task-editor',
    templateUrl: './math-task-editor.component.html',
    styleUrl: './math-task-editor.component.scss',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: MathTaskEditorComponent,
        },
        {
            provide: NG_VALIDATORS,
            multi: true,
            useExisting: MathTaskEditorComponent,
        },
    ],
})
export class MathTaskEditorComponent implements ControlValueAccessor, OnInit, OnDestroy {
    private onTouched = () => {};
    private onChangeSubs: Subscription[] = [];
    private formRecord: FormRecord<FormControl<MathTaskInput['value']>>;

    protected inputType: MathTaskEditorInputType = MathTaskEditorInputType.EXPRESSION;
    protected expressionControl: FormControl<MathTaskExpressionValue>;

    protected readonly MathTaskEditorInputType = MathTaskEditorInputType;

    @Input()
    exerciseId: number;

    constructor(private fb: FormBuilder) {}

    ngOnInit() {
        this.expressionControl = this.fb.control(
            { expression: '' },
            {
                nonNullable: true,
                validators: [Validators.required],
            },
        );
        this.formRecord = this.fb.record({
            EXPRESSION: this.expressionControl,
        });
    }

    ngOnDestroy() {
        for (const sub of this.onChangeSubs) {
            sub.unsubscribe();
        }
    }

    writeValue(input: MathTaskInput | undefined | null) {
        if (!input) return;

        this.inputType = input.type;

        const inputTypeKey = MathTaskEditorInputType[input.type];

        this.formRecord.patchValue({ [inputTypeKey]: input.value }, { emitEvent: false });
    }

    registerOnChange(onChange: (_: MathTaskInput) => void) {
        this.onChangeSubs.push(
            this.formRecord.valueChanges
                .pipe(
                    concatMap(Object.entries),
                    filter(([_, v]) => !!v),
                    map(([k, value]) => ({ type: MathTaskEditorInputType[k], value })),
                )
                .subscribe(onChange),
        );
    }

    registerOnTouched(fn: () => void) {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        if (isDisabled) {
            this.formRecord.disable();
        } else {
            this.formRecord.enable();
        }
    }

    validate(): ValidationErrors | null {
        if (this.formRecord.valid) {
            return null;
        }

        return this.formRecord.errors;
    }
}
