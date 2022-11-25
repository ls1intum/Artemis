import dayjs from 'dayjs/esm';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faTimes } from '@fortawesome/free-solid-svg-icons';

export interface VideoUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
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
})
export class VideoUnitFormComponent implements OnInit, OnChanges {
    @Input()
    formData: VideoUnitFormData;
    @Input()
    isEditMode = false;

    @Output()
    formSubmitted: EventEmitter<VideoUnitFormData> = new EventEmitter<VideoUnitFormData>();
    form: FormGroup;

    @Input()
    hasCancelButton: boolean;
    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();

    faTimes = faTimes;

    urlValidator = urlValidator;
    videoUrlValidator = videoUrlValidator;

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
            name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
            description: [undefined as string | undefined, [Validators.maxLength(1000)]],
            releaseDate: [undefined as dayjs.Dayjs | undefined],
            source: [undefined as string | undefined, [Validators.required, this.urlValidator]],
            urlHelper: [undefined as string | undefined, this.videoUrlValidator],
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

    setEmbeddedVideoUrl(event: any) {
        event.stopPropagation();
        this.sourceControl!.setValue(this.extractEmbeddedUrl(this.urlHelperControl!.value));
    }

    extractEmbeddedUrl(videoUrl: string) {
        return urlParser.create({
            videoInfo: urlParser.parse(videoUrl)!,
            format: 'embed',
        });
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
