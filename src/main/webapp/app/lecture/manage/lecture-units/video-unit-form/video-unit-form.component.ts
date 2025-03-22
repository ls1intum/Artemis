import dayjs from 'dayjs/esm';
import { Component, computed, effect, inject, input, output, untracked, viewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export interface VideoUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    source?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
}

function isTumLiveUrl(url: URL): boolean {
    return url.host === 'live.rbg.tum.de';
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
    selector: 'jhi-video-unit-form',
    templateUrl: './video-unit-form.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FormDateTimePickerComponent, CompetencySelectionComponent, FaIconComponent, ArtemisTranslatePipe],
})
export class VideoUnitFormComponent {
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;

    private readonly formBuilder = inject(FormBuilder);

    formData = input<VideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<VideoUnitFormData>();

    hasCancelButton = input<boolean>();
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        source: [undefined as string | undefined, [Validators.required, this.videoSourceUrlValidator]],
        urlHelper: [undefined as string | undefined, this.videoSourceTransformUrlValidator],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');
    isFormValid = computed(() => this.statusChanges() === 'VALID' && this.datePickerComponent()?.isValid());

    constructor() {
        effect(() => {
            if (this.isEditMode() && this.formData()) {
                untracked(() => this.setFormValues(this.formData()!));
            }
        });
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

    get sourceControl() {
        return this.form.get('source');
    }

    get urlHelperControl() {
        return this.form.get('urlHelper');
    }

    private setFormValues(formData: VideoUnitFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const videoUnitFormData: VideoUnitFormData = { ...this.form.value };
        this.formSubmitted.emit(videoUnitFormData);
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
