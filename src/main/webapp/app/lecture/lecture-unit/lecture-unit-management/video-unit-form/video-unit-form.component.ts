import { Moment } from 'moment';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';

export interface VideoUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: Moment;
    source?: string;
}

function videoUrlValidator(control: AbstractControl) {
    if (control.value === undefined || control.value === null || control.value === '') {
        return null;
    }

    const videoInfo = urlParser.parse(control.value);
    return videoInfo ? null : { invalidVideoUrl: true };
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
    selector: 'jhi-video-unit-form',
    templateUrl: './video-unit-form.component.html',
    styleUrls: ['./video-unit-form.component.scss'],
})
export class VideoUnitFormComponent implements OnInit, OnChanges {
    @Input()
    formData: VideoUnitFormData;
    @Input()
    isEditMode = false;

    @Output()
    formSubmitted: EventEmitter<VideoUnitFormData> = new EventEmitter<VideoUnitFormData>();
    form: FormGroup;

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
            description: [undefined, [Validators.maxLength(255)]],
            releaseDate: [undefined],
            source: [undefined, [Validators.required, urlValidator]],
            urlHelper: [undefined, videoUrlValidator],
        });
    }

    private setFormValues(formData: VideoUnitFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const videoUnitFormData: VideoUnitFormData = { ...this.form.value };
        this.formSubmitted.emit(videoUnitFormData);
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

    createEmbeddableVideoUrl(event: any) {
        event.stopPropagation();
        this.sourceControl!.setValue(
            urlParser.create({
                videoInfo: urlParser.parse(this.urlHelperControl!.value)!,
                format: 'embed',
            }),
        );
    }
}
