import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDateRangePipe } from 'app/shared/pipes/artemis-date-range.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

export interface TutorialGroupsConfigurationFormData {
    period?: Date[];
    usePublicTutorialGroupChannels?: boolean;
    useTutorialGroupChannels?: boolean;
}

@Component({
    selector: 'jhi-tutorial-groups-configuration-form',
    templateUrl: './tutorial-groups-configuration-form.component.html',
    styleUrls: ['./tutorial-groups-configuration-form.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, OwlDateTimeModule, FaIconComponent, ArtemisDateRangePipe],
    providers: [ArtemisDatePipe],
})
export class TutorialGroupsConfigurationFormComponent implements OnInit, OnChanges {
    private fb = inject(FormBuilder);

    @Input()
    formData: TutorialGroupsConfigurationFormData = {
        period: undefined,
        usePublicTutorialGroupChannels: false,
        useTutorialGroupChannels: false,
    };
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupsConfigurationFormData> = new EventEmitter<TutorialGroupsConfigurationFormData>();

    @Input()
    course: Course;

    faCalendarAlt = faCalendarAlt;

    readonly isMessagingEnabled = isMessagingEnabled;

    existingChannelSetting?: boolean;

    form: FormGroup;

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
    ngOnChanges() {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }
    private setFormValues(formData: TutorialGroupsConfigurationFormData) {
        this.existingChannelSetting = formData.useTutorialGroupChannels;
        this.form.patchValue(formData);
    }

    get showChannelDeletionWarning() {
        if (!this.isEditMode) {
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
            period: [undefined, Validators.required],
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
