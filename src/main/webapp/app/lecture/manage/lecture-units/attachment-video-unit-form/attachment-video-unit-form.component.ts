import { Component, ElementRef, OnChanges, ViewChild, computed, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faQuestionCircle, faTimes, faVideo } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
}

// matches structure of the reactive form
export interface FormProperties {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    updateNotificationText?: string;
    videoSource?: string;
    urlHelper?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
}

function isTumLiveUrl(url: URL): boolean {
    const tumLiveUrls = ['live.rbg.tum.de', 'tum.live'];
    return tumLiveUrls.includes(url.host);
}

function isVideoOnlyTumUrl(url: URL): boolean {
    return url?.searchParams.get('video_only') === '1';
}

function videoSourceTransformUrlValidator(control: AbstractControl): ValidationErrors | undefined {
    const urlValue = control.value;
    if (!urlValue) {
        return undefined;
    }
    let parsedUrl, url;
    try {
        url = new URL(urlValue);
        parsedUrl = urlParser.parse(urlValue);
    } catch {
        //intentionally empty
    }
    // The URL is valid if it's a TUM-Live URL or if it can be parsed by the js-video-url-parser.
    if ((url && isTumLiveUrl(url)) || parsedUrl) {
        return undefined;
    }
    return { invalidVideoUrl: true };
}

function videoSourceUrlValidator(control: AbstractControl): ValidationErrors | undefined {
    const urlValue = control.value;
    if (!urlValue) {
        return undefined;
    }
    let url;
    try {
        url = new URL(control.value);
    } catch {
        // intentionally empty
    }
    if (url && !(isTumLiveUrl(url) && !isVideoOnlyTumUrl(url))) {
        return undefined;
    }
    return { invalidVideoUrl: true };
}

@Component({
    selector: 'jhi-attachment-video-unit-form',
    templateUrl: './attachment-video-unit-form.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FaIconComponent, NgbTooltip, FormDateTimePickerComponent, CompetencySelectionComponent, ArtemisTranslatePipe],
})
export class AttachmentVideoUnitFormComponent implements OnChanges {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faVideo = faVideo;
    private readonly http = inject(HttpClient);

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    file: File;
    fileInputTouched = false;

    @ViewChild('videoUploadInput', { static: false }) videoUploadInput: ElementRef;
    videoFile: File;

    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    private readonly formBuilder = inject(FormBuilder);
    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        version: [{ value: 1, disabled: true }],
        videoSource: [undefined as string | undefined, this.videoSourceUrlValidator],
        urlHelper: [undefined as string | undefined, this.videoSourceTransformUrlValidator],
        updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');

    readonly videoSourceSignal = toSignal(this.videoSourceControl!.valueChanges, { initialValue: this.videoSourceControl!.value });

    isFormValid = computed(() => {
        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.datePickerComponent()?.isValid() && (!!this.fileName() || !!this.videoSourceSignal());
    });

    ngOnChanges() {
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData()!);
        }
    }

    onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }
        this.file = input.files[0];
        this.fileName.set(this.file.name);
        // automatically set the name in case it is not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                name: this.file.name.replace(/\.[^/.]+$/, ''),
            });
        }
        this.isFileTooBig.set(this.file.size > MAX_FILE_SIZE);
    }

    onVideoUploadChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }

        this.videoFile = input.files[0];
        this.fileName.set(this.videoFile.name);
        this.isFileTooBig.set(false); // allow large videos
    }

    get nameControl() {
        return this.form.get('name');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    get updateNotificationTextControl() {
        return this.form.get('updateNotificationText');
    }

    get versionControl() {
        return this.form.get('version');
    }

    get videoSourceControl() {
        return this.form.get('videoSource');
    }

    get urlHelperControl() {
        return this.form.get('urlHelper');
    }

    async submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };
        const fileProperties: FileProperties = {
            file: this.videoFile || this.file,
            fileName: this.fileName(),
        };

        // If a video file was uploaded, call backend API to upload and get video ID
        if (this.videoFile) {
            const formData = new FormData();
            formData.append('file', this.videoFile);

            try {
                formProperties.videoSource = await lastValueFrom(this.http.post('api/lecture/video/upload', formData, { responseType: 'text' })); // Save the returned videoId into videoSource
            } catch (error) {
                // eslint-disable-next-line no-undef
                console.error('Video upload failed:', error);
                return;
            }
        }

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
        });
    }

    private setFormValues(formData: AttachmentVideoUnitFormData) {
        if (formData?.formProperties) {
            this.form.patchValue(formData.formProperties);
        }
        if (formData?.fileProperties?.file) {
            this.file = formData?.fileProperties?.file;
        }
        if (formData?.fileProperties?.fileName) {
            this.fileName.set(formData?.fileProperties?.fileName);
        }
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
        const embeddedUrl = this.extractEmbeddedUrl(this.urlHelperControl!.value);
        this.videoSourceControl!.setValue(embeddedUrl);
    }

    extractEmbeddedUrl(videoUrl: string) {
        const url = new URL(videoUrl);
        if (isTumLiveUrl(url)) {
            url.searchParams.set('video_only', '1');
            return url.toString();
        }
        return urlParser.create({
            videoInfo: urlParser.parse(videoUrl)!,
            format: 'embed',
        });
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
