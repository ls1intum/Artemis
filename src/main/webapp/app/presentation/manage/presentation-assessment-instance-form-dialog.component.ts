import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { merge, pairwise } from 'rxjs';
import dayjs from 'dayjs/esm';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonComponent,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiInputNumberComponent,
    TumAetUiMessageComponent,
    TumAetUiSelectComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { User } from 'app/account/user/user.model';
import {
    PresentationAssessment,
    PresentationAssessmentInstance,
    PresentationAssessmentInstanceRequest,
    PresentationAssessmentInstancesCreate,
    PresentationAssessmentMode,
} from 'app/presentation/shared/entities/presentation-assessment.model';
import { TranslateService } from '@ngx-translate/core';
import { PresentationAssessmentPresenterSelectorComponent } from 'app/presentation/manage/presentation-assessment-presenter-selector.component';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const resultPoints = control.get('resultPoints')?.value;
    const maxPoints = control.get('maxPoints')?.value;
    return resultPoints !== null && resultPoints !== undefined && Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
};

/** A meeting link is rendered as a link, so without a scheme the browser would open it relative to the Artemis URL. */
const httpUrl: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    typeof control.value === 'string' && control.value.trim().length > 0 && !/^https?:\/\/\S+$/i.test(control.value.trim()) ? { pattern: true } : null;

const RESULT_POINTS_UPPER_BOUND = 10000;
const MIN_PRESENTATION_DATE = dayjs('1970-01-01T00:00:00');
const minimumPresentationDate: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    control.value && dayjs(control.value).isBefore(MIN_PRESENTATION_DATE) ? { minDate: true } : null;
export type PresentationAssessmentInstanceFormResult =
    { kind: 'create'; request: PresentationAssessmentInstancesCreate } | { kind: 'update'; instance: PresentationAssessmentInstanceRequest };

@Component({
    selector: 'jhi-presentation-assessment-instance-form-dialog',
    templateUrl: './presentation-assessment-instance-form-dialog.component.html',
    styleUrl: './presentation-assessment-instance-form-dialog.component.scss',
    imports: [
        ReactiveFormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        PresentationAssessmentPresenterSelectorComponent,
        TumAetUiButtonComponent,
        TumAetUiDatePickerComponent,
        TumAetUiInputDirective,
        TumAetUiInputNumberComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
    ],
})
export class PresentationAssessmentInstanceFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly translateService = inject(TranslateService);
    private readonly translationChanges = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange));

    readonly courseId = input.required<number>();
    readonly presentationAssessment = input.required<PresentationAssessment>();
    readonly instance = input<PresentationAssessmentInstance>();
    readonly initialAssignedStudents = input<User[]>([]);
    readonly isSaving = input(false);
    readonly saved = output<PresentationAssessmentInstanceFormResult>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;
    protected readonly dateTextValid = signal(true);
    protected readonly timeTextValid = signal(true);
    protected readonly resultPointsUpperBound = RESULT_POINTS_UPPER_BOUND;
    protected readonly acceptedPointsDecimalSeparators = ['.', ','];
    readonly languageOptions = computed(() => {
        this.translationChanges();
        return [
            { label: this.translateService.instant('artemisApp.presentationAssessment.languageOptions.english'), value: 'en' },
            { label: this.translateService.instant('artemisApp.presentationAssessment.languageOptions.german'), value: 'de' },
        ];
    });
    readonly modeOptions = computed(() => {
        this.translationChanges();
        return [
            { label: this.translateService.instant('artemisApp.presentationAssessment.mode.online'), value: PresentationAssessmentMode.ONLINE },
            { label: this.translateService.instant('artemisApp.presentationAssessment.mode.inPerson'), value: PresentationAssessmentMode.IN_PERSON },
        ];
    });

    readonly assignedStudents = signal<User[]>([]);

    editForm = this.formBuilder.group(
        {
            presentationDate: [undefined as dayjs.Dayjs | undefined, [Validators.required, minimumPresentationDate]],
            presentationTime: [undefined as dayjs.Dayjs | undefined],
            resultPoints: [undefined as number | undefined, [Validators.min(0), Validators.max(RESULT_POINTS_UPPER_BOUND)]],
            maxPoints: [0],
            language: ['en', Validators.required],
            mode: [PresentationAssessmentMode.IN_PERSON, Validators.required],
            location: ['', Validators.maxLength(255)],
            meetingLink: ['', [Validators.maxLength(1000), httpUrl]],
            remark: ['', Validators.maxLength(1000)],
        },
        { validators: resultPointsDoNotExceedMaxPoints },
    );

    constructor() {
        this.editForm.controls.mode.valueChanges.pipe(pairwise(), takeUntilDestroyed()).subscribe(([previousMode, mode]) => {
            if (previousMode === PresentationAssessmentMode.ONLINE && mode === PresentationAssessmentMode.IN_PERSON && this.editForm.controls.meetingLink.invalid) {
                this.editForm.controls.meetingLink.setValue('');
            }
            if (previousMode === PresentationAssessmentMode.IN_PERSON && mode === PresentationAssessmentMode.ONLINE && this.editForm.controls.location.invalid) {
                this.editForm.controls.location.setValue('');
            }
        });
        effect(() => {
            const instance = this.instance();
            this.dateTextValid.set(true);
            this.timeTextValid.set(true);
            this.assignedStudents.set([...this.initialAssignedStudents()]);
            this.editForm.reset({
                presentationDate: instance?.presentationDate?.startOf('day'),
                presentationTime: instance?.presentationDate,
                resultPoints: instance?.resultPoints,
                maxPoints: this.presentationAssessment().maxPoints ?? 0,
                language: instance?.language ?? 'en',
                mode: instance?.mode ?? PresentationAssessmentMode.IN_PERSON,
                location: instance?.location ?? '',
                meetingLink: instance?.meetingLink ?? '',
                remark: instance?.remark ?? '',
            });
        });
        effect(() => (this.isSaving() ? this.editForm.disable({ emitEvent: false }) : this.editForm.enable({ emitEvent: false })));
    }

    save(): void {
        const instance = this.instance();

        if (this.isSaving() || this.editForm.invalid || !this.dateTextValid() || !this.timeTextValid()) {
            this.editForm.markAllAsTouched();
            return;
        }

        const value = this.editForm.getRawValue();
        if (!value.presentationDate || !value.language || !value.mode) {
            return;
        }

        const presentationDate = dayjs(value.presentationDate);
        const presentationTime = value.presentationTime ? dayjs(value.presentationTime) : undefined;
        const combinedPresentationDate = presentationDate
            .hour(presentationTime?.hour() ?? 0)
            .minute(presentationTime?.minute() ?? 0)
            .second(0)
            .millisecond(0);

        const data = {
            presentationDate: combinedPresentationDate,
            resultPoints: value.resultPoints ?? undefined,
            language: value.language,
            mode: value.mode,
            location: value.mode === PresentationAssessmentMode.IN_PERSON ? value.location?.trim() || undefined : undefined,
            meetingLink: value.mode === PresentationAssessmentMode.ONLINE ? value.meetingLink?.trim() || undefined : undefined,
            remark: value.remark?.trim() || undefined,
        } satisfies Omit<PresentationAssessmentInstancesCreate, 'studentLogins'>;

        if (instance) {
            const studentLogin = instance.student?.login;
            if (instance.id === undefined || !studentLogin) {
                return;
            }
            this.saved.emit({
                kind: 'update',
                instance: cloneWith(data, {
                    id: instance.id,
                    studentLogin,
                }),
            });
        } else {
            const studentLogins = [
                ...new Set(
                    this.assignedStudents()
                        .map((student) => student.login)
                        .filter((login): login is string => !!login),
                ),
            ];

            if (studentLogins.length === 0) {
                this.editForm.markAllAsTouched();
                return;
            }
            this.saved.emit({
                kind: 'create',
                request: cloneWith(data, { studentLogins }),
            });
        }
    }

    cancel(): void {
        this.cancelled.emit();
    }
}
