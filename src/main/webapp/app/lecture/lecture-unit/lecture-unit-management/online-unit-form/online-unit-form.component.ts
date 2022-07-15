import dayjs from 'dayjs/esm';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { OnlineResourceDTO } from 'app/lecture/lecture-unit/lecture-unit-management/online-resource-dto.model';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';

export interface OnlineUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    source?: string;
}

function urlValidator(control: AbstractControl) {
    let validUrl = true;

    try {
        // tslint:disable-next-line:no-unused-expression-chai
        new URL(control.value);
    } catch {
        validUrl = false;
    }

    return validUrl ? null : { invalidUrl: true };
}

@Component({
    selector: 'jhi-online-unit-form',
    templateUrl: './online-unit-form.component.html',
})
export class OnlineUnitFormComponent implements OnInit, OnChanges {
    @Input()
    formData: OnlineUnitFormData;
    @Input()
    isEditMode = false;

    @Output()
    formSubmitted: EventEmitter<OnlineUnitFormData> = new EventEmitter<OnlineUnitFormData>();
    form: FormGroup;

    urlValidator = urlValidator;

    // Icons
    faArrowLeft = faArrowLeft;

    constructor(private fb: FormBuilder, private onlineUnitService: OnlineUnitService) {}

    get nameControl() {
        return this.form.get('name');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    get sourceControl() {
        return this.form.get('source');
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            name: [undefined, [Validators.required, Validators.maxLength(255)]],
            description: [undefined, [Validators.maxLength(1000)]],
            releaseDate: [undefined],
            source: [undefined, [Validators.required, this.urlValidator]],
        });
    }

    private setFormValues(formData: OnlineUnitFormData) {
        this.form.patchValue(formData);
    }

    /**
     * When the link is changed, fetch the website's metadata and update the form
     */
    onLinkChanged(): void {
        const value = this.sourceControl?.value;

        // If the link does not start with any HTTP protocol then add it
        const regex = /https?:\/\//;
        if (value && !value.match(regex)) {
            this.sourceControl?.setValue('https://' + value);
        }

        if (this.sourceControl?.valid) {
            this.onlineUnitService
                .getOnlineResource(this.sourceControl.value)
                .pipe(map((response: HttpResponse<OnlineResourceDTO>) => response.body!))
                .subscribe({
                    next: (onlineResource) => {
                        const updateForm = {
                            name: onlineResource.title || undefined,
                            description: onlineResource.description || undefined,
                        };
                        this.form.patchValue(updateForm);
                    },
                });
        }
    }

    submitForm() {
        const onlineUnitFormData: OnlineUnitFormData = { ...this.form.value };
        this.formSubmitted.emit(onlineUnitFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }
}
