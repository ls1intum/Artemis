import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import dayjs from 'dayjs/esm';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiInputDirective, TumUiInputNumberComponent, TumUiMessageComponent, TumUiSelectComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { User } from 'app/account/user/user.model';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { TranslateService } from '@ngx-translate/core';
import { deepClone } from 'app/foundation/util/deep-clone.util';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const resultPoints = control.get('resultPoints')?.value;
    const maxPoints = control.get('maxPoints')?.value;
    return resultPoints !== null && resultPoints !== undefined && Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
};

const RESULT_POINTS_UPPER_BOUND = 10000;
const MIN_PRESENTATION_DATE = dayjs('1970-01-01T00:00:00');
const wholeNumber: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    control.value !== undefined && control.value !== null && !Number.isInteger(Number(control.value)) ? { wholeNumber: true } : null;
const minimumPresentationDate: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    control.value && dayjs(control.value).isBefore(MIN_PRESENTATION_DATE) ? { minDate: true } : null;

@Component({
    selector: 'jhi-presentation-assessment-instance-form-dialog',
    templateUrl: './presentation-assessment-instance-form-dialog.component.html',
    styleUrl: './presentation-assessment-instance-form-dialog.component.scss',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        FaIconComponent,
        TranslateDirective,
        FormDateTimePickerComponent,
        CourseGroupComponent,
        TumUiButtonComponent,
        TumUiInputDirective,
        TumUiInputNumberComponent,
        TumUiMessageComponent,
        TumUiSelectComponent,
    ],
})
export class PresentationAssessmentInstanceFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly translateService = inject(TranslateService);

    readonly courseId = input.required<number>();
    readonly course = input.required<Course>();
    readonly presentationAssessment = input.required<PresentationAssessment>();
    readonly instance = input<PresentationAssessmentInstance>();
    readonly initialAssignedStudents = input<User[]>([]);
    readonly isSaving = input(false);
    readonly saved = output<PresentationAssessmentInstance>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly studentsCourseGroup = CourseRoleSlug.STUDENTS;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;
    protected readonly DateTimePickerType = DateTimePickerType;
    protected readonly hiddenStudentColumnFields = ['id', 'visibleRegistrationNumber', 'email'];
    protected readonly resultPointsUpperBound = RESULT_POINTS_UPPER_BOUND;
    protected readonly minPresentationDate = MIN_PRESENTATION_DATE;
    readonly languageOptions = [
        { label: 'English', value: 'en' },
        { label: 'Deutsch', value: 'de' },
    ];
    readonly modeOptions = computed(() => [
        { label: this.translateService.instant('artemisApp.presentationAssessment.mode.online'), value: PresentationAssessmentMode.ONLINE },
        { label: this.translateService.instant('artemisApp.presentationAssessment.mode.inPerson'), value: PresentationAssessmentMode.IN_PERSON },
    ]);

    readonly assignedStudents = signal<User[]>([]);
    readonly filteredAssignedStudentsSize = signal(0);

    readonly presentationStudentCourse = computed(() => {
        const presentationCourse: Course = deepClone(this.course());
        presentationCourse.isAtLeastInstructor = false;
        return presentationCourse;
    });

    editForm = this.formBuilder.group(
        {
            presentationDate: [undefined as dayjs.Dayjs | undefined, [Validators.required, minimumPresentationDate]],
            presentationTime: [undefined as dayjs.Dayjs | undefined],
            resultPoints: [undefined as number | undefined, [wholeNumber, Validators.min(0), Validators.max(RESULT_POINTS_UPPER_BOUND)]],
            maxPoints: [0],
            language: ['en', Validators.required],
            mode: [PresentationAssessmentMode.IN_PERSON, Validators.required],
            location: ['', Validators.maxLength(255)],
            meetingLink: ['', Validators.maxLength(1000)],
            remark: ['', Validators.maxLength(1000)],
        },
        { validators: resultPointsDoNotExceedMaxPoints },
    );

    constructor() {
        effect(() => {
            const instance = this.instance();
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
        if (this.isSaving() || this.editForm.invalid || this.assignedStudents().length === 0) {
            this.editForm.markAllAsTouched();
            return;
        }
        const value = this.editForm.getRawValue();
        const presentationDate = dayjs(value.presentationDate);
        const presentationTime = value.presentationTime ? dayjs(value.presentationTime) : undefined;
        const combinedPresentationDate = presentationDate
            .hour(presentationTime?.hour() ?? 0)
            .minute(presentationTime?.minute() ?? 0)
            .second(0)
            .millisecond(0);
        this.saved.emit({
            id: this.instance()?.id,
            presentationDate: combinedPresentationDate,
            resultPoints: value.resultPoints ?? undefined,
            studentLogins: [
                ...new Set(
                    this.assignedStudents()
                        .map((student) => student.login)
                        .filter((login): login is string => !!login),
                ),
            ],
            language: value.language ?? undefined,
            mode: value.mode ?? undefined,
            location: value.mode === PresentationAssessmentMode.IN_PERSON ? value.location?.trim() || undefined : undefined,
            meetingLink: value.mode === PresentationAssessmentMode.ONLINE ? value.meetingLink?.trim() || undefined : undefined,
            remark: value.remark?.trim() || undefined,
        } satisfies PresentationAssessmentInstance);
    }

    cancel(): void {
        this.cancelled.emit();
    }

    studentSearch = (loginOrName: string): Observable<HttpResponse<User[]>> => this.courseManagementService.searchStudents(this.courseId(), loginOrName);
    addStudent = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());
    removeStudent = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());
    handleAssignedStudentsSizeChange = (size: number): void => this.filteredAssignedStudentsSize.set(size);
}
