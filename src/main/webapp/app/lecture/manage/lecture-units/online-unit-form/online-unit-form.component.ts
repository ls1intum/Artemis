import dayjs from 'dayjs/esm';
import { Component, OnChanges, computed, inject, input, output, viewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { faArrowLeft, faTimes } from '@fortawesome/free-solid-svg-icons';
import { map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { OnlineResourceDTO } from 'app/lecture/manage/lecture-units/online-resource-dto.model';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';

export interface OnlineUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    source?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
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
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FormDateTimePickerComponent, CompetencySelectionComponent, FaIconComponent, ArtemisTranslatePipe],
})
export class OnlineUnitFormComponent implements OnChanges {
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faTimes = faTimes;

    formData = input<OnlineUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<OnlineUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    urlValidator = urlValidator;

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

    ngOnChanges() {
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData()!);
        }
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
        const onlineUnitFormData: OnlineUnitFormData = Object.assign({}, this.form.value);
        this.formSubmitted.emit(onlineUnitFormData);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
