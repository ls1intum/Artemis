import { ChangeDetectionStrategy, Component, OnInit, effect, inject, input, output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { Course, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export interface TutorialGroupsConfigurationFormData {
    period?: Date[];
    usePublicTutorialGroupChannels?: boolean;
    useTutorialGroupChannels?: boolean;
}

/**
 * Validates the tutorial period selected via the range p-datepicker.
 * Replaces owl's range validators: returns `{ required }` when the range is missing/incomplete and
 * `{ invalidRange }` when the start date is after the end date.
 */
export function tutorialPeriodRangeValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (!Array.isArray(value) || value.length < 2 || !(value[0] instanceof Date) || !(value[1] instanceof Date) || isNaN(value[0].getTime()) || isNaN(value[1].getTime())) {
        return { required: true };
    }
    const [start, end] = value as Date[];
    if (start.getTime() > end.getTime()) {
        return { invalidRange: true };
    }
    return null;
}

@Component({
    selector: 'jhi-tutorial-groups-configuration-form',
    templateUrl: './tutorial-groups-configuration-form.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, DatePickerModule, ArtemisTranslatePipe],
})
export class TutorialGroupsConfigurationFormComponent implements OnInit {
    private fb = inject(FormBuilder);

    readonly formData = input<TutorialGroupsConfigurationFormData>({
        period: undefined,
        usePublicTutorialGroupChannels: false,
        useTutorialGroupChannels: false,
    });
    readonly isEditMode = input(false);
    readonly formSubmitted = output<TutorialGroupsConfigurationFormData>();

    readonly course = input.required<Course>();

    readonly isMessagingEnabled = isMessagingEnabled;

    existingChannelSetting?: boolean;

    form: FormGroup;

    constructor() {
        // Effect to handle formData changes (replaces ngOnChanges)
        effect(() => {
            const formData = this.formData();
            const editMode = this.isEditMode();
            this.initializeForm();
            if (editMode && formData) {
                this.setFormValues(formData);
            }
        });
    }

    get periodControl() {
        return this.form.get('period');
    }

    get useTutorialGroupChannelsControl() {
        return this.form.get('useTutorialGroupChannels');
    }

    get usePublicTutorialGroupChannelsControl() {
        return this.form.get('usePublicTutorialGroupChannels');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.initializeForm();
    }
    private setFormValues(formData: TutorialGroupsConfigurationFormData) {
        this.existingChannelSetting = formData.useTutorialGroupChannels;
        this.form.patchValue(formData);
    }

    get showChannelDeletionWarning() {
        if (!this.isEditMode()) {
            return false;
        }
        if (this.existingChannelSetting === undefined) {
            return false;
        }
        return this.existingChannelSetting && this.useTutorialGroupChannelsControl?.value === false;
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            period: [undefined, tutorialPeriodRangeValidator],
            useTutorialGroupChannels: [false],
            usePublicTutorialGroupChannels: [false],
        });
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value });
    }

    get isPeriodInvalid() {
        if (this.periodControl) {
            return this.periodControl.invalid && (this.periodControl.touched || this.periodControl.dirty);
        } else {
            return false;
        }
    }

    markPeriodAsTouched() {
        if (this.periodControl) {
            this.periodControl.markAsTouched();
        }
    }
}
