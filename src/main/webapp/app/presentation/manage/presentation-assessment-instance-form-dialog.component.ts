import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Observable, Subject, merge, of, pairwise } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiAutoCompleteComponent,
    TumAetUiButtonComponent,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiSelectComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { User, UserNameAndLoginDTO } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import {
    PresentationAssessment,
    PresentationAssessmentInstance,
    PresentationAssessmentInstanceRequest,
    PresentationAssessmentInstancesCreate,
    PresentationAssessmentMode,
} from 'app/presentation/shared/entities/presentation-assessment.model';
import { TranslateService } from '@ngx-translate/core';
import { PresentationAssessmentPresenterSelectorComponent } from 'app/presentation/manage/presentation-assessment-presenter-selector.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { hasResultPoints } from 'app/presentation/manage/presentation-assessment-management.helper';
import { PointsInputComponent } from 'app/shared-ui/tum-aet-ui-integration/points-input/points-input.component';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const resultPoints = control.get('resultPoints')?.value;
    const maxPoints = control.get('maxPoints')?.value;
    return resultPoints !== null && resultPoints !== undefined && Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
};

/**
 * Only a graded presentation has an assessor, so the assessor is required as soon as result points are entered. The autocomplete writes the typed text into
 * the control, so only a selected assessor (an object) counts; without result points neither is checked, as the field is hidden then.
 */
const assessorSelectedForGrade: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    if (!hasResultPoints(control.get('resultPoints')?.value)) {
        return null;
    }
    const assessorOption = control.get('assessorOption')?.value;
    if (!assessorOption) {
        return { assessorRequired: true };
    }
    return typeof assessorOption === 'string' ? { assessorNotSelected: true } : null;
};

/** A meeting link is rendered as a link, so without a scheme the browser would open it relative to the Artemis URL. */
const httpUrl: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    typeof control.value === 'string' && control.value.trim().length > 0 && !/^https?:\/\/\S+$/i.test(control.value.trim()) ? { pattern: true } : null;

const RESULT_POINTS_UPPER_BOUND = 10000;
const MIN_PRESENTATION_DATE = dayjs('1970-01-01T00:00:00');
const minimumPresentationDate: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    control.value && dayjs(control.value).isBefore(MIN_PRESENTATION_DATE) ? { minDate: true } : null;

/** The assessor together with the text the autocomplete shows for it, like the presenter options of the presenter selector. */
interface AssessorOption {
    assessor: UserNameAndLoginDTO;
    label: string;
}

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
        TumAetUiAutoCompleteComponent,
        TumAetUiButtonComponent,
        TumAetUiDatePickerComponent,
        TumAetUiInputDirective,
        PointsInputComponent,
        ProfilePictureComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
    ],
})
export class PresentationAssessmentInstanceFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly translateService = inject(TranslateService);
    private readonly accountService = inject(AccountService);
    private readonly alertService = inject(AlertService);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly translationChanges = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange));

    readonly courseId = input.required<number>();
    readonly presentationAssessment = input.required<PresentationAssessment>();
    readonly instance = input<PresentationAssessmentInstance>();
    readonly initialAssignedStudents = input<User[]>([]);
    readonly isSaving = input(false);
    /** Editors and instructors choose the assessor; tutors keep the given one or assess themselves. */
    readonly canChooseAssessor = input(false);
    readonly saved = output<PresentationAssessmentInstanceFormResult>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;
    protected readonly dateTextValid = signal(true);
    protected readonly timeTextValid = signal(true);
    protected readonly resultPointsUpperBound = RESULT_POINTS_UPPER_BOUND;
    protected readonly hasResultPoints = hasResultPoints;
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

    private readonly assessorSearchTerms = new Subject<string>();

    readonly searchAssessors = (searchTerms: Observable<string>): Observable<UserNameAndLoginDTO[]> =>
        searchTerms.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            switchMap((term) => {
                if (term.trim().length < 3) {
                    return of([]);
                }
                return this.presentationAssessmentService.searchAssessors(this.courseId(), term).pipe(
                    map((response) => response.body ?? []),
                    catchError(() => {
                        this.alertService.error('artemisApp.presentationAssessment.assessorSearchFailed');
                        return of([]);
                    }),
                );
            }),
        );

    protected readonly assessorSuggestions = toSignal(this.searchAssessors(this.assessorSearchTerms), { initialValue: [] });

    protected searchAssessorsByQuery(query: string): void {
        this.assessorSearchTerms.next(query);
    }

    /** The selected assessor, or undefined while the field is empty or holds text typed into the autocomplete. */
    protected selectedAssessorOption(): AssessorOption | undefined {
        const value = this.editForm.controls.assessorOption.value;
        return value && typeof value === 'object' ? value : undefined;
    }

    readonly assessorFormatter = (assessor: UserNameAndLoginDTO): string => `${assessor.name ?? ''} (${assessor.login ?? ''})`;

    protected readonly assessorOptions = computed(() => this.assessorSuggestions().map((assessor) => this.toAssessorOption(assessor)));

    editForm = this.formBuilder.group(
        {
            presentationDate: [undefined as dayjs.Dayjs | undefined, [Validators.required, minimumPresentationDate]],
            presentationTime: [undefined as dayjs.Dayjs | undefined],
            resultPoints: [undefined as number | undefined, [Validators.min(0), Validators.max(RESULT_POINTS_UPPER_BOUND)]],
            maxPoints: [0],
            assessorOption: [undefined as AssessorOption | undefined],
            language: ['en', Validators.required],
            mode: [PresentationAssessmentMode.IN_PERSON, Validators.required],
            location: ['', Validators.maxLength(255)],
            meetingLink: ['', [Validators.maxLength(1000), httpUrl]],
            remark: ['', Validators.maxLength(1000)],
        },
        { validators: [resultPointsDoNotExceedMaxPoints, assessorSelectedForGrade] },
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
                assessorOption: this.initialAssessorOption(instance),
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
        const assessorLogin = hasResultPoints(value.resultPoints) ? this.selectedAssessorOption()?.assessor.login : undefined;
        if (!value.presentationDate || !value.language || !value.mode || (hasResultPoints(value.resultPoints) && !assessorLogin)) {
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
            assessorLogin,
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

    private toAssessorOption(assessor: UserNameAndLoginDTO): AssessorOption {
        return { assessor, label: this.assessorFormatter(assessor) };
    }

    /** An existing instance keeps its assessor; a new one, or one graded before assessors were stored, is assessed by the current user. */
    private initialAssessorOption(instance?: PresentationAssessmentInstance): AssessorOption | undefined {
        const assessor = instance?.assessor ?? untracked(() => this.accountService.userIdentity());
        return assessor ? this.toAssessorOption({ name: assessor.name, login: assessor.login }) : undefined;
    }
}
