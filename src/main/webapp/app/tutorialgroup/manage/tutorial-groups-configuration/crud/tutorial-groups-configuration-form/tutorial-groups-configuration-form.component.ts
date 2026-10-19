import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import dayjs, { Dayjs } from 'dayjs/esm';
import { Course, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiDatePickerComponent, TumAetUiMessageComponent, TumAetUiSelectButtonComponent } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGear } from '@fortawesome/free-solid-svg-icons';

export interface TutorialGroupsConfigurationFormData {
    period?: Date[];
    usePublicTutorialGroupChannels?: boolean;
    useTutorialGroupChannels?: boolean;
}

/**
 * Validates the tutorial period, a `[start, end]` pair of dates picked in the form's start and end date pickers.
 * Returns `{ required }` when the period is missing/incomplete and `{ invalidRange }` when the start date is after
 * the end date.
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

function isValidDate(value: unknown): value is Date {
    return value instanceof Date && !isNaN(value.getTime());
}

@Component({
    selector: 'jhi-tutorial-groups-configuration-form',
    templateUrl: './tutorial-groups-configuration-form.component.html',
    styleUrl: './tutorial-groups-configuration-form.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ReactiveFormsModule,
        TranslateDirective,
        TumAetUiDatePickerComponent,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectButtonComponent,
        FaIconComponent,
    ],
})
export class TutorialGroupsConfigurationFormComponent implements OnInit {
    private fb = inject(FormBuilder);
    private destroyRef = inject(DestroyRef);

    protected readonly faGear = faGear;

    readonly formData = input<TutorialGroupsConfigurationFormData>({
        period: undefined,
        usePublicTutorialGroupChannels: false,
        useTutorialGroupChannels: false,
    });
    readonly isEditMode = input(false);
    readonly formSubmitted = output<TutorialGroupsConfigurationFormData>();

    readonly course = input.required<Course>();

    readonly isMessagingEnabled = isMessagingEnabled;

    /** Public vs. private channel, as the two sides of the select button. */
    protected readonly channelVisibilityOptions = [
        { value: true, labelKey: 'artemisApp.dialogs.createChannel.channelForm.isPublicInput.public' },
        { value: false, labelKey: 'artemisApp.dialogs.createChannel.channelForm.isPublicInput.private' },
    ];

    existingChannelSetting?: boolean;

    /**
     * The two ends of the period as the pickers hold them. The form's `period` control stays the one source of truth
     * for validation and for the parent: it holds `[start, end]` once both are set and nothing else, so a half-filled
     * or half-typed period reads as missing.
     */
    protected readonly periodStart = signal<Dayjs | undefined>(undefined);
    protected readonly periodEnd = signal<Dayjs | undefined>(undefined);
    // A picker keeps its last committed date while its text is not a date, so the period must not use it meanwhile.
    private readonly periodStartTextValid = signal(true);
    private readonly periodEndTextValid = signal(true);

    form!: FormGroup; // built by initializeForm() in the constructor effect and ngOnInit, before any template/getter read

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
        this.periodControl?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((period) => this.pullPeriodFromControl(period));
    }

    submitForm() {
        this.formSubmitted.emit(deepClone(this.form.value));
    }

    /** Id of the message below the pickers that explains the invalid period, for the pickers to point at. */
    get periodErrorId(): string | undefined {
        if (!this.isPeriodInvalid) {
            return undefined;
        }
        return this.periodControl?.errors?.invalidRange ? 'period-range-error' : 'period-required-error';
    }

    /** The start is flagged when it is missing or when the end falls before it. */
    get isPeriodStartInvalid(): boolean {
        return this.isPeriodInvalid && (!this.periodStart() || !!this.periodControl?.errors?.invalidRange);
    }

    /** The end is flagged when it is missing or when it falls before the start. */
    get isPeriodEndInvalid(): boolean {
        return this.isPeriodInvalid && (!this.periodEnd() || !!this.periodControl?.errors?.invalidRange);
    }

    get isPeriodInvalid() {
        if (this.periodControl) {
            return this.periodControl.invalid && (this.periodControl.touched || this.periodControl.dirty);
        } else {
            return false;
        }
    }

    protected onPeriodStartChange(start?: Dayjs) {
        this.periodStart.set(start);
        this.pushPeriodToControl();
        this.periodControl?.markAsDirty();
    }

    protected onPeriodEndChange(end?: Dayjs) {
        this.periodEnd.set(end);
        this.pushPeriodToControl();
        this.periodControl?.markAsDirty();
    }

    protected onPeriodStartTextValidityChange(valid: boolean) {
        if (this.periodStartTextValid() !== valid) {
            this.periodStartTextValid.set(valid);
            this.pushPeriodToControl();
        }
    }

    protected onPeriodEndTextValidityChange(valid: boolean) {
        if (this.periodEndTextValid() !== valid) {
            this.periodEndTextValid.set(valid);
            this.pushPeriodToControl();
        }
    }

    /** Writes the pickers' dates to the period control without echoing them back to the pickers. */
    private pushPeriodToControl() {
        const start = this.periodStart();
        const end = this.periodEnd();
        const complete = !!start && !!end && this.periodStartTextValid() && this.periodEndTextValid();
        this.periodControl?.setValue(complete ? [start.toDate(), end.toDate()] : undefined, { emitEvent: false });
    }

    /** Shows what the period control holds, when it was set from outside the pickers, such as when editing. */
    private pullPeriodFromControl(period?: unknown) {
        const [start, end] = Array.isArray(period) ? period : [];
        this.periodStart.set(isValidDate(start) ? dayjs(start) : undefined);
        this.periodEnd.set(isValidDate(end) ? dayjs(end) : undefined);
    }

    markPeriodAsTouched() {
        if (this.periodControl) {
            this.periodControl.markAsTouched();
        }
    }
}
