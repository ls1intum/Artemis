import dayjs from 'dayjs/esm';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';

export interface OnlineUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    source?: string;
}

function onlineUrlValidator(control: AbstractControl) {
    if (control.value === undefined || control.value === null || control.value === '') {
        return null;
    }

    // const onlineInfo = urlParser.parse(control.value);
    // return onlineInfo ? null : { invalidOnlineUrl: true };
    return null;
}

function urlValidator(control: AbstractControl) {
    let validUrl = true;

    // for certain cases like embed links for vimeo
    const regex = /^\/\/.*$/;
    if (control.value && control.value.match(regex)) {
        return null;
    }

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
    onlineUrlValidator = onlineUrlValidator;

    // Icons
    faArrowLeft = faArrowLeft;

    constructor(private fb: FormBuilder) {}

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

    get urlHelperControl() {
        return this.form.get('urlHelper');
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
            urlHelper: [undefined, this.onlineUrlValidator],
        });
    }

    private setFormValues(formData: OnlineUnitFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const onlineUnitFormData: OnlineUnitFormData = { ...this.form.value };
        this.formSubmitted.emit(onlineUnitFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    get isTransformable() {
        if (this.urlHelperControl!.value === undefined || this.urlHelperControl!.value === null || this.urlHelperControl!.value === '') {
            return false;
        } else {
            return !this.urlHelperControl?.invalid;
        }
    }

    setEmbeddedOnlineUrl(event: any) {
        event.stopPropagation();
        // this.sourceControl!.setValue(this.extractEmbeddedUrl(this.urlHelperControl!.value));
    }

    extractEmbeddedUrl(onlineUrl: string) {
        /*return urlParser.create({
            onlineInfo: urlParser.parse(onlineUrl)!,
            format: 'embed',
        });*/
    }
}
