import { Component, DestroyRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Observable, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';
import { deepClone } from 'app/foundation/util/deep-clone.util';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const maxPoints = parseNumber(control.get('maxPoints')?.value);
    const resultPoints = parseNumber(control.get('resultPoints')?.value);
    return maxPoints !== undefined && resultPoints !== undefined && resultPoints > maxPoints ? { resultPointsExceedMaxPoints: true } : null;
};

const numberRequired: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    return parseNumber(control.value) === undefined ? { numberRequired: true } : null;
};

const optionalNumber: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    return !isBlank(value) && parseNumber(value) === undefined ? { numberRequired: true } : null;
};

const numberMin =
    (min: number): ValidatorFn =>
    (control: AbstractControl): ValidationErrors | null => {
        const value = parseNumber(control.value);
        return value !== undefined && value < min ? { min: true } : null;
    };

const isBlank = (value: unknown): boolean => value === undefined || value === null || value === '';

const parseNumber = (value: unknown): number | undefined => {
    const parsedValue = isBlank(value) ? undefined : typeof value === 'string' ? Number(value.trim().replace(',', '.')) : Number(value);
    return Number.isFinite(parsedValue) ? parsedValue : undefined;
};

export interface PresentationAssessmentFormDialogResult {
    presentationAssessment: PresentationAssessment;
    assignedStudents: User[];
    originalAssignedStudents: User[];
}

@Component({
    selector: 'jhi-presentation-assessment-form-dialog',
    templateUrl: './presentation-assessment-form-dialog.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FormDateTimePickerComponent,
        CourseGroupComponent,
        FaIconComponent,
        TumUiButtonComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
    ],
})
export class PresentationAssessmentFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly datePicker = viewChild(FormDateTimePickerComponent);

    readonly courseId = input.required<number>();
    readonly course = input<Course>();
    readonly presentationAssessment = input<PresentationAssessment>();
    readonly initialAssignedStudents = input<User[]>([]);
    readonly isSaving = input(false);

    readonly saved = output<PresentationAssessmentFormDialogResult>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly studentsCourseGroup = CourseRoleSlug.STUDENTS;

    readonly assignedStudents = signal<User[]>([]);
    readonly filteredAssignedStudentsSize = signal(0);

    private originalAssignedStudents: User[] = [];

    readonly presentationStudentCourse = computed<Course | undefined>(() => {
        const course = this.course();
        if (!course) {
            return undefined;
        }
        const presentationCourse: Course = deepClone(course);
        presentationCourse.isAtLeastInstructor = false;
        return presentationCourse;
    });

    readonly studentExportFilename = computed(() => {
        const title = this.presentationAssessment()?.title?.trim();
        return title ? `${title} Students` : 'Presentation Students';
    });

    editForm = this.formBuilder.group(
        {
            title: ['', [Validators.required, Validators.maxLength(255)]],
            description: ['', [Validators.maxLength(1000)]],
            maxPoints: [0 as number | string | undefined, [numberRequired, numberMin(0.01)]],
            resultPoints: [undefined as number | string | undefined, [optionalNumber, numberMin(0)]],
            presentationDate: [undefined as dayjs.Dayjs | undefined],
        },
        { validators: resultPointsDoNotExceedMaxPoints },
    );

    readonly currentTitle = signal('');
    readonly studentSectionTitle = computed(() => this.currentTitle().trim() || this.presentationAssessment()?.title?.trim() || 'New presentation');

    constructor() {
        this.editForm.controls.title.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((title) => this.currentTitle.set(title ?? ''));

        effect(() => {
            const presentationAssessment = this.presentationAssessment();
            const initialAssignedStudents = this.initialAssignedStudents();
            this.originalAssignedStudents = [...initialAssignedStudents];
            this.assignedStudents.set([...initialAssignedStudents]);
            this.editForm.reset({
                title: presentationAssessment?.title ?? '',
                description: presentationAssessment?.description ?? '',
                maxPoints: presentationAssessment?.maxPoints ?? 20,
                resultPoints: presentationAssessment?.resultPoints,
                presentationDate: presentationAssessment?.presentationDate,
            });
            this.currentTitle.set(this.editForm.controls.title.value ?? '');
        });

        effect(() => (this.isSaving() ? this.editForm.disable({ emitEvent: false }) : this.editForm.enable({ emitEvent: false })));
    }

    save(): void {
        if (this.isSaving()) {
            return;
        }

        if (this.editForm.invalid || this.datePicker()?.dateInput.valid === false) {
            this.editForm.markAllAsTouched();
            return;
        }

        this.saved.emit({
            presentationAssessment: this.createFromForm(),
            assignedStudents: this.assignedStudents(),
            originalAssignedStudents: this.originalAssignedStudents,
        } satisfies PresentationAssessmentFormDialogResult);
    }

    cancel(): void {
        if (this.isSaving()) {
            return;
        }

        this.cancelled.emit();
    }

    studentSearch = (loginOrName: string): Observable<HttpResponse<User[]>> => this.courseManagementService.searchStudents(this.courseId(), loginOrName);

    addStudentToPresentation = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());

    removeStudentFromPresentation = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());

    handleAssignedStudentsSizeChange = (filteredAssignedStudentsSize: number): void => this.filteredAssignedStudentsSize.set(filteredAssignedStudentsSize);

    private createFromForm(): PresentationAssessment {
        const formValue = this.editForm.getRawValue();
        return {
            id: this.presentationAssessment()?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            maxPoints: parseNumber(formValue.maxPoints),
            resultPoints: parseNumber(formValue.resultPoints),
            presentationDate: formValue.presentationDate ? dayjs(formValue.presentationDate) : undefined,
            courseId: this.courseId(),
        };
    }
}
