import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, forkJoin, map, merge, of, pairwise, startWith, switchMap, tap } from 'rxjs';
import dayjs from 'dayjs/esm';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faCheck, faSave, faXmark } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiAutoCompleteComponent,
    TumAetUiButtonComponent,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiRadioButtonComponent,
    TumAetUiSelectComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { onError } from 'app/foundation/util/global.utils';
import { Course } from 'app/course/shared/entities/course.model';
import { User, UserNameAndLoginDTO } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import {
    PresentationAssessment,
    PresentationAssessmentInstance,
    PresentationAssessmentInstanceRequest,
    PresentationAssessmentInstancesCreate,
    PresentationAssessmentMode,
    PresentationGradingMethod,
} from 'app/presentation/shared/entities/presentation-assessment.model';
import { TranslateService } from '@ngx-translate/core';
import { PresentationAssessmentPresenterSelectorComponent } from 'app/presentation/manage/presentation-assessment-presenter-selector.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { PointsInputComponent } from 'app/shared-ui/tum-aet-ui-integration/points-input/points-input.component';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { PresentationRubricGradingComponent } from 'app/presentation/manage/presentation-rubric-grading.component';
import { RatingsForm, countUnrated, createRatingsForm, readRatingsForm } from 'app/presentation/manage/presentation-rubric-rating-form';
import { isSet } from 'app/presentation/shared/presentation.util';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const resultPoints = control.get('resultPoints')?.value;
    const maxPoints = control.get('maxPoints')?.value;
    return resultPoints !== null && resultPoints !== undefined && Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
};

/**
 * Only a graded presentation has an assessor, so the assessor is required as soon as the form holds a result. The autocomplete writes the typed text into
 * the control, so only a selected assessor (an object) counts; without a result neither is checked, as the field is hidden then.
 */
function assessorSelectedForResult(hasResult: () => boolean): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        if (!hasResult()) {
            return null;
        }
        const assessorOption = control.get('assessorOption')?.value;
        if (!assessorOption) {
            return { assessorRequired: true };
        }
        return typeof assessorOption === 'string' ? { assessorNotSelected: true } : null;
    };
}

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

/** The criteria still to rate while a rubric is rated incompletely, or 0 while it is rated completely or, being optional, not at all. */
function remainingCriteriaOf(ratingsForm: RatingsForm): number {
    return ratingsForm.hasError('ratingsIncomplete') || ratingsForm.hasError('ratingsRequired') ? countUnrated(ratingsForm) : 0;
}
const OUTCOMES = [
    { key: 'passed', value: true, icon: faCheck },
    { key: 'failed', value: false, icon: faXmark },
] as const;
export type PresentationAssessmentInstanceFormResult =
    { kind: 'create'; request: PresentationAssessmentInstancesCreate } | { kind: 'update'; instance: PresentationAssessmentInstanceRequest };

@Component({
    selector: 'jhi-presentation-assessment-instance-form-dialog',
    templateUrl: './presentation-assessment-instance-form-dialog.component.html',
    imports: [
        NgTemplateOutlet,
        FormsModule,
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
        TumAetUiProgressSpinnerComponent,
        TumAetUiRadioButtonComponent,
        TumAetUiSelectComponent,
        PresentationRubricGradingComponent,
    ],
})
export class PresentationAssessmentInstanceFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly translateService = inject(TranslateService);
    private readonly accountService = inject(AccountService);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly translationChanges = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange));

    readonly courseId = input.required<number>();
    /** The course, whose settings round the preview of a rubric result. */
    readonly course = input<Course>();
    readonly presentationAssessment = input.required<PresentationAssessment>();
    readonly instance = input<PresentationAssessmentInstance>();
    readonly initialAssignedStudents = input<User[]>([]);
    readonly isSaving = input(false);
    /** Instructors choose the assessor; tutors and editors keep the given one or assess themselves. */
    readonly canChooseAssessor = input(false);
    readonly saved = output<PresentationAssessmentInstanceFormResult>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;
    protected readonly PresentationGradingMethod = PresentationGradingMethod;
    protected readonly outcomes = OUTCOMES;
    protected readonly dateTextValid = signal(true);
    protected readonly timeTextValid = signal(true);
    protected readonly resultPointsUpperBound = RESULT_POINTS_UPPER_BOUND;
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

    protected readonly gradingMethod = computed(() => this.presentationAssessment().gradingMethod);

    /** The rubric and, when editing, the saved ratings; only a presentation graded by rubric loads them. */
    private readonly rubricResource = rxResource({
        params: () => {
            const presentationAssessment = this.presentationAssessment();
            return presentationAssessment.gradingMethod === PresentationGradingMethod.RUBRIC && presentationAssessment.id !== undefined
                ? { courseId: this.courseId(), presentationAssessmentId: presentationAssessment.id, instanceId: this.instance()?.id }
                : undefined;
        },
        stream: ({ params }) =>
            forkJoin({
                criteria: this.presentationAssessmentService.getRubric(params.courseId, params.presentationAssessmentId).pipe(map((response) => response.body?.criteria ?? [])),
                ratings:
                    params.instanceId === undefined
                        ? of([])
                        : this.presentationAssessmentService
                              .getRatings(params.courseId, params.presentationAssessmentId, params.instanceId)
                              .pipe(map((response) => response.body ?? [])),
            }).pipe(tap({ error: (error: HttpErrorResponse) => onError(this.alertService, error) })),
    });
    protected readonly isRubricLoading = computed(() => this.rubricResource.isLoading());
    protected readonly criteria = computed(() => (this.rubricResource.hasValue() ? this.rubricResource.value().criteria : []));
    /** One rating per criterion; a saved rubric result can be changed but not removed, so its ratings stay required. */
    protected readonly ratingsForm = computed(() => {
        if (!this.rubricResource.hasValue()) {
            return undefined;
        }
        const { criteria, ratings } = this.rubricResource.value();
        return createRatingsForm(criteria, ratings, isSet(this.instance()?.rubricScore));
    });
    /** Saving without the loaded rubric would drop the ratings of the instance. */
    protected readonly rubricUnavailable = computed(() => this.gradingMethod() === PresentationGradingMethod.RUBRIC && !this.ratingsForm());
    /** The number of criteria still to rate while the rubric is rated incompletely, updated with every change of the ratings. */
    protected readonly remainingCriteria = toSignal(
        toObservable(this.ratingsForm).pipe(
            switchMap((ratingsForm) =>
                ratingsForm
                    ? ratingsForm.events.pipe(
                          startWith(undefined),
                          map(() => remainingCriteriaOf(ratingsForm)),
                      )
                    : of(0),
            ),
        ),
        { initialValue: 0 },
    );

    editForm = this.formBuilder.group(
        {
            presentationDate: [undefined as dayjs.Dayjs | undefined, [Validators.required, minimumPresentationDate]],
            presentationTime: [undefined as dayjs.Dayjs | undefined],
            resultPoints: [undefined as number | undefined, [Validators.min(0), Validators.max(RESULT_POINTS_UPPER_BOUND)]],
            passed: [undefined as boolean | undefined],
            maxPoints: [0],
            assessorOption: [undefined as AssessorOption | undefined],
            language: ['en', Validators.required],
            mode: [PresentationAssessmentMode.IN_PERSON, Validators.required],
            location: ['', Validators.maxLength(255)],
            meetingLink: ['', [Validators.maxLength(1000), httpUrl]],
            remark: ['', Validators.maxLength(1000)],
        },
        // Read untracked, as the form also validates inside effects, which would otherwise rerun and reset it whenever the result state changes
        { validators: [resultPointsDoNotExceedMaxPoints, assessorSelectedForResult(() => untracked(() => this.hasResult()))] },
    );

    /**
     * Whether the form holds a result of the grading method; only a graded presentation has an assessor. A rubric counts as soon as one criterion is rated,
     * as only then its ratings are sent. The form validates itself while it is built, before it is assigned, when nothing is entered yet.
     */
    protected hasResult(): boolean {
        if (!this.editForm) {
            return false;
        }
        switch (this.gradingMethod()) {
            case PresentationGradingMethod.PASS_FAIL:
                return isSet(this.editForm.controls.passed.value);
            case PresentationGradingMethod.RUBRIC: {
                const ratingsForm = this.ratingsForm();
                return !!ratingsForm && countUnrated(ratingsForm) < ratingsForm.length;
            }
            default:
                return isSet(this.editForm.controls.resultPoints.value);
        }
    }

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
            // A saved score can be changed but not removed
            this.editForm.controls.resultPoints.setValidators([
                Validators.min(0),
                Validators.max(RESULT_POINTS_UPPER_BOUND),
                ...(isSet(instance?.resultPoints) ? [Validators.required] : []),
            ]);
            this.editForm.reset({
                presentationDate: instance?.presentationDate?.startOf('day'),
                presentationTime: instance?.presentationDate,
                resultPoints: instance?.resultPoints,
                passed: instance?.passed ?? undefined,
                maxPoints: this.presentationAssessment().maxPoints ?? 0,
                assessorOption: this.initialAssessorOption(instance),
                language: instance?.language ?? 'en',
                mode: instance?.mode ?? PresentationAssessmentMode.IN_PERSON,
                location: instance?.location ?? '',
                meetingLink: instance?.meetingLink ?? '',
                remark: instance?.remark ?? '',
            });
        });
        effect(() => {
            const ratingsForm = this.ratingsForm();
            if (this.isSaving()) {
                this.editForm.disable({ emitEvent: false });
                ratingsForm?.disable({ emitEvent: false });
            } else {
                this.editForm.enable({ emitEvent: false });
                ratingsForm?.enable({ emitEvent: false });
            }
        });
        // A rated criterion makes the assessor required, so the ratings validate the form again like the other results do
        effect((onCleanup) => {
            const subscription = this.ratingsForm()?.valueChanges.subscribe(() => this.editForm.updateValueAndValidity());
            onCleanup(() => subscription?.unsubscribe());
        });
    }

    save(): void {
        const instance = this.instance();
        const ratingsForm = this.ratingsForm();

        if (this.isSaving() || this.rubricUnavailable() || this.editForm.invalid || ratingsForm?.invalid || !this.dateTextValid() || !this.timeTextValid()) {
            this.editForm.markAllAsTouched();
            ratingsForm?.markAllAsTouched();
            return;
        }

        const value = this.editForm.getRawValue();
        const graded = this.hasResult();
        const assessorLogin = graded ? this.selectedAssessorOption()?.assessor.login : undefined;
        if (!value.presentationDate || !value.language || !value.mode || (graded && !assessorLogin)) {
            return;
        }

        const presentationDate = dayjs(value.presentationDate);
        const presentationTime = value.presentationTime ? dayjs(value.presentationTime) : undefined;
        const combinedPresentationDate = presentationDate
            .hour(presentationTime?.hour() ?? 0)
            .minute(presentationTime?.minute() ?? 0)
            .second(0)
            .millisecond(0);

        const gradingMethod = this.gradingMethod();
        const data = {
            presentationDate: combinedPresentationDate,
            resultPoints: gradingMethod === PresentationGradingMethod.SCORE ? (value.resultPoints ?? undefined) : undefined,
            passed: gradingMethod === PresentationGradingMethod.PASS_FAIL ? (value.passed ?? undefined) : undefined,
            ratings: ratingsForm ? readRatingsForm(this.criteria(), ratingsForm) : undefined,
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
