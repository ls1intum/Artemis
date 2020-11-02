import { Moment } from 'moment';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';

const getVideoId = require('get-video-id');

export interface VideoUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: Moment;
    source?: string;
}

export function extractVideoService(url: string) {
    if (!url) {
        return undefined;
    }
    return getVideoId(url).service;
}

export function extractVideoId(url: string) {
    if (!url) {
        return undefined;
    }
    return getVideoId(url).id;
}

export function youtubeURLValidator(c: AbstractControl) {
    if (c.value === undefined || c.value === null || c.value === '' || extractVideoService(c.value) === 'youtube') {
        return null;
    }
    return {
        youTubeUrl: c.value,
    };
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

    get urlControl() {
        return this.form.get('url');
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode) {
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
            source: [undefined, Validators.required],
            url: [undefined, youtubeURLValidator],
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

    get isIdExtractable() {
        if (this.urlControl!.value === undefined || this.urlControl!.value === null || this.urlControl!.value === '') {
            return false;
        } else {
            return !this.urlControl?.invalid;
        }
    }

    extractId(event: any) {
        event.stopPropagation();
        this.sourceControl!.setValue(extractVideoId(this.urlControl?.value));
    }
}
