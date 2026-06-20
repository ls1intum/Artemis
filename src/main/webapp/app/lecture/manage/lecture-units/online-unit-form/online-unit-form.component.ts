import dayjs from 'dayjs/esm';
import { Component, computed, effect, inject, input, output, viewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { faArrowLeft, faTimes } from '@fortawesome/free-solid-svg-icons';
import { map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { OnlineResourceDTO } from 'app/lecture/manage/lecture-units/online-resource-dto.model';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { GocastStreamPickerComponent } from 'app/videosource/gocast/gocast-stream-picker.component';

export interface OnlineUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    source?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
    /** Set when a TUM Live stream is selected via the stream picker (Stage 2). */
    gocastStreamId?: number;
}

function urlValidator(control: AbstractControl) {
    let validUrl = true;

    try {
        new URL(control.value);
    } catch {
        validUrl = false;
    }

    return validUrl ? null : { invalidUrl: true };
}

@Component({
    selector: 'jhi-online-unit-form',
    templateUrl: './online-unit-form.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        FormDateTimePickerComponent,
        CompetencySelectionComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
        GocastStreamPickerComponent,
    ],
})
export class OnlineUnitFormComponent {
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faTimes = faTimes;

    formData = input<OnlineUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<OnlineUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    /**
     * The Artemis course id. When provided, the TUM Live stream picker (Stage 2) is rendered
     * below the URL field. The picker self-resolves the binding status and only shows the
     * stream dropdown when the binding is ACTIVE.
     */
    courseId = input<number | undefined>(undefined);

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    urlValidator = urlValidator;

    /** streamId selected via the TUM Live stream picker; included in the emitted form data. */
    private selectedGocastStreamId: number | undefined;
    /** The URL the picker auto-filled into the source field; cleared together with the stream on de-selection. */
    private lastAutoFilledGocastUrl: string | undefined;

    private readonly formBuilder = inject(FormBuilder);
    private readonly onlineUnitService = inject(OnlineUnitService);

    form: FormGroup = this.formBuilder.group({
        name: [undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined],
        source: [undefined, [Validators.required, this.urlValidator]],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });

    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');
    isFormValid = computed(() => this.statusChanges() === 'VALID' && this.datePickerComponent()?.isValid());

    // Tracks the formData reference already applied to the form so the patching effect stays idempotent.
    private appliedFormData?: OnlineUnitFormData;

    constructor() {
        // Patch the form with the provided data in edit mode (replaces ngOnChanges).
        // Patch ONCE per distinct formData value: form.patchValue() synchronously emits statusChanges,
        // which is mirrored into the `statusChanges` signal via toSignal(...). Under zoneless that signal
        // write reschedules the reactive flush, which re-runs this effect, which patches again — an
        // infinite change-detection loop that leaves the edit form stuck behind the loading spinner.
        // Guarding on the formData reference breaks the cycle and avoids clobbering in-progress edits.
        effect(() => {
            const data = this.formData();
            if (this.isEditMode() && data && data !== this.appliedFormData) {
                this.appliedFormData = data;
                this.setFormValues(data);
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

    /**
     * Called when the TUM Live stream picker (Stage 2) selection changes.
     * Records the chosen streamId for inclusion in the submitted form data, or clears it
     * (and the auto-filled URL) when the picker selection is cleared.
     */
    onGocastStreamSelected(event: { streamId: number; streamName: string; slug?: string } | undefined): void {
        if (!event) {
            // Selection cleared — drop the cached id and remove the URL we auto-filled.
            if (this.selectedGocastStreamId !== undefined && this.sourceControl?.value === this.lastAutoFilledGocastUrl) {
                this.sourceControl?.setValue('');
            }
            this.selectedGocastStreamId = undefined;
            this.lastAutoFilledGocastUrl = undefined;
            return;
        }
        this.selectedGocastStreamId = event.streamId;
        // Auto-fill the source field with the TUM Live watch-page URL.
        // Format: https://tum.live/w/{courseSlug}/{streamId} — required by TumLiveService regex.
        // Only write when the field is empty or still holds a value we previously auto-filled,
        // so a URL the user typed/edited by hand is preserved. This also keeps the URL in sync
        // when the picker selection changes from one stream to another.
        const currentValue = this.sourceControl?.value;
        const canAutoFill = !currentValue || currentValue === this.lastAutoFilledGocastUrl;
        if (canAutoFill && event.slug) {
            const url = `https://tum.live/w/${event.slug}/${event.streamId}`;
            this.sourceControl?.setValue(url);
            this.lastAutoFilledGocastUrl = url;
        }
    }

    submitForm() {
        const onlineUnitFormData: OnlineUnitFormData = {
            ...this.form.value,
            gocastStreamId: this.selectedGocastStreamId,
        };
        this.formSubmitted.emit(onlineUnitFormData);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
