import dayjs from 'dayjs/esm';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Competency } from 'app/entities/competency.model';

export interface VideoUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    source?: string;
    competencies?: Competency[];
}

function isTumLiveUrl(url: URL): boolean {
    return url.host === 'live.rbg.tum.de';
}

function isVideoOnlyTumUrl(url: URL): boolean {
    return url?.searchParams.get('video_only') === '1';
}

function videoSourceTransformUrlValidator(control: AbstractControl): ValidationErrors | null {
    const invalidVideoUrlError = { invalidVideoUrl: true };
    const urlValue = control.value;
    if (!urlValue) return null;
    try {
        return isTumLiveUrl(new URL(urlValue)) || urlParser.parse(urlValue) ? null : invalidVideoUrlError;
    } catch {
        return invalidVideoUrlError;
    }
}

function videoSourceUrlValidator(control: AbstractControl): ValidationErrors | null {
    const invalidVideoUrlError = { invalidVideoUrl: true };
    try {
        const urlValue = control.value;
        const url = new URL(urlValue);
        if (isTumLiveUrl(url)) return isVideoOnlyTumUrl(url) ? null : invalidVideoUrlError;
        return urlParser.parse(urlValue) ? invalidVideoUrlError : null;
    } catch {
        return invalidVideoUrlError;
    }
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

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

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
            source: [undefined as string | undefined, [Validators.required, this.videoSourceUrlValidator]],
            urlHelper: [undefined as string | undefined, this.videoSourceTransformUrlValidator],
            competencies: [undefined as Competency[] | undefined],
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
        const url = new URL(videoUrl);
        const isTumUrl = isTumLiveUrl(url);
        if (isTumUrl && !isVideoOnlyTumUrl(url)) {
            url.searchParams.set('video_only', '1');
        }
        return isTumUrl
            ? url.toString()
            : urlParser.create({
                  videoInfo: urlParser.parse(videoUrl)!,
                  format: 'embed',
              });
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
