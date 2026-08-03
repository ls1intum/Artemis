import { Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Observable, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { Course, CourseGroup } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { deepClone } from 'app/foundation/util/deep-clone.util';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const maxPoints = control.get('maxPoints')?.value;
    const resultPoints = control.get('resultPoints')?.value;

    if (maxPoints === undefined || maxPoints === null || resultPoints === undefined || resultPoints === null) {
        return null;
    }

    return Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
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

    readonly courseId = input.required<number>();
    readonly course = input<Course>();
    readonly presentationAssessment = input<PresentationAssessment>();
    readonly initialAssignedStudents = input<User[]>([]);

    readonly saved = output<PresentationAssessmentFormDialogResult>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly studentsCourseGroup = CourseGroup.STUDENTS;

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
            maxPoints: [0, [Validators.required, Validators.min(0.01)]],
            resultPoints: [undefined as number | undefined, [Validators.min(0)]],
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
    }

    save(): void {
        if (this.editForm.invalid) {
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
            maxPoints: formValue.maxPoints ?? undefined,
            resultPoints: formValue.resultPoints ?? undefined,
            presentationDate: formValue.presentationDate ? dayjs(formValue.presentationDate) : undefined,
            courseId: this.courseId(),
        };
    }
}
