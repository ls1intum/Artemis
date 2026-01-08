import { ChangeDetectionStrategy, Component, OnInit, effect, inject, input, output } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgbTimeAdapter, NgbTimepicker } from '@ng-bootstrap/ng-bootstrap';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { validTimeRange } from 'app/tutorialgroup/shared/util/timeRangeValidator';
import { NgbTimeStringAdapter } from 'app/tutorialgroup/shared/util/ngbTimeStringAdapter';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';

export interface TutorialGroupSessionFormData {
    date?: Date;
    startTime?: string;
    endTime?: string;
    location?: string;
}

@Component({
    selector: 'jhi-tutorial-group-session-form',
    templateUrl: './tutorial-group-session-form.component.html',
    providers: [{ provide: NgbTimeAdapter, useClass: NgbTimeStringAdapter }],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, OwlDateTimeModule, NgbTimepicker, ArtemisTranslatePipe, FormDateTimePickerComponent],
})
export class TutorialGroupSessionFormComponent implements OnInit {
    private fb = inject(FormBuilder);
    protected readonly DateTimePickerType = DateTimePickerType;

    readonly formData = input<TutorialGroupSessionFormData>({
        date: undefined,
        startTime: undefined,
        endTime: undefined,
        location: undefined,
    });

    readonly timeZone = input<string>();
    readonly isEditMode = input(false);
    readonly formSubmitted = output<TutorialGroupSessionFormData>();
    faCalendarAlt = faCalendarAlt;

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

    get dateControl() {
        return this.form.get('date');
    }

    get startTimeControl() {
        return this.form.get('startTime');
    }

    get endTimeControl() {
        return this.form.get('endTime');
    }

    get locationControl() {
        return this.form.get('location');
    }

    get isSubmitPossible() {
        return !this.form.invalid && this.form.get('date')?.value !== null;
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    submitForm() {
        const formValue = this.form.value;
        // Creating a TutorialGroupSessionFormData is currently neccessary till component gets rewritten to modern angular
        const tutorialGroupSessionFormData: TutorialGroupSessionFormData = {
            date: formValue.date ? new Date(formValue.date) : undefined,
            startTime: formValue.startTime,
            endTime: formValue.endTime,
            location: formValue.location,
        };
        this.formSubmitted.emit(tutorialGroupSessionFormData);
    }

    private setFormValues(formData: TutorialGroupSessionFormData) {
        this.form.patchValue(formData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group(
            {
                startTime: ['13:00:00', [Validators.required]],
                endTime: ['14:00:00', [Validators.required]],
                date: [undefined],
                location: [undefined, [Validators.required]],
            },
            { validators: validTimeRange },
        );
    }
}
