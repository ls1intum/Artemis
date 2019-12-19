import { Directive, Input, OnInit } from '@angular/core';
import { Validator, AbstractControl } from '@angular/forms';
import { NG_VALIDATORS } from '@angular/forms';
import { FileUploadExerciseUpdateComponent } from 'app/entities/file-upload-exercise';

@Directive({
    selector: '[jhiFilePatternInput]',
    providers: [{ provide: NG_VALIDATORS, useExisting: FilePatternValidatorDirective, multi: true }],
})
export class FilePatternValidatorDirective implements Validator, OnInit {
    @Input('jhiFilePatternInput') filePattern: string;

    constructor(private fileUploadExerciseComponent: FileUploadExerciseUpdateComponent) {}

    validPatterns: string[] = [];

    ngOnInit() {
        this.validPatterns = this.fileUploadExerciseComponent.fileUploadExerciseSetting.filePatterns;
    }

    validate(control: AbstractControl): { [key: string]: any } | null {
        return validateFilePattern(control.value, this.validPatterns) ? null : { forbidden: { value: control.value } };
    }
}

function validateFilePattern(input: String | null, validPatterns: string[]): boolean {
    return !!input && input.split(',').every(pattern => Object.values(validPatterns).includes(pattern.replace(/\s/g, '').toLowerCase()));
}
