import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Observable, of } from 'rxjs';

import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';

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

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const maxPoints = control.get('maxPoints')?.value;
    const resultPoints = control.get('resultPoints')?.value;

    if (maxPoints === undefined || maxPoints === null || resultPoints === undefined || resultPoints === null) {
        return null;
    }

    return Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
};

export interface PresentationAssessmentFormDialogData {
    courseId: number;
    course?: Course;
    presentationAssessment?: PresentationAssessment;
    assignedStudents: User[];
}

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
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        MessageModule,
        TextareaModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FormDateTimePickerComponent,
        CourseGroupComponent,
        FaIconComponent,
    ],
})
export class PresentationAssessmentFormDialogComponent implements OnInit {
    private readonly formBuilder = inject(FormBuilder);
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);
    private readonly courseManagementService = inject(CourseManagementService);

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly studentsCourseGroup = CourseGroup.STUDENTS;

    readonly assignedStudents = signal<User[]>([]);
    readonly filteredAssignedStudentsSize = signal(0);

    private courseId = 0;
    private presentationAssessment?: PresentationAssessment;
    private course?: Course;
    private originalAssignedStudents: User[] = [];

    readonly presentationStudentCourse = computed<Course | undefined>(() => {
        if (!this.course) {
            return undefined;
        }
        const presentationCourse: Course = Object.assign({}, this.course);
        presentationCourse.isAtLeastInstructor = false;
        return presentationCourse;
    });

    readonly studentExportFilename = computed(() => {
        const title = this.presentationAssessment?.title?.trim();
        return title ? `${title} Students` : 'Presentation Students';
    });

    readonly studentSectionTitle = computed(() => this.presentationAssessment?.title?.trim() || this.editForm.controls.title.value?.trim() || 'New presentation');

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

    ngOnInit(): void {
        const data = this.dialogConfig.data as PresentationAssessmentFormDialogData;
        this.courseId = data.courseId;
        this.course = data.course;
        this.presentationAssessment = data.presentationAssessment;
        this.originalAssignedStudents = [...data.assignedStudents];
        this.assignedStudents.set([...data.assignedStudents]);

        this.editForm.reset({
            title: this.presentationAssessment?.title ?? '',
            description: this.presentationAssessment?.description ?? '',
            maxPoints: this.presentationAssessment?.maxPoints ?? 20,
            resultPoints: this.presentationAssessment?.resultPoints,
            presentationDate: this.presentationAssessment?.presentationDate,
        });
    }

    save(): void {
        if (this.editForm.invalid) {
            this.editForm.markAllAsTouched();
            return;
        }

        this.dialogRef.close({
            presentationAssessment: this.createFromForm(),
            assignedStudents: this.assignedStudents(),
            originalAssignedStudents: this.originalAssignedStudents,
        } satisfies PresentationAssessmentFormDialogResult);
    }

    cancel(): void {
        this.dialogRef.close();
    }

    studentSearch = (loginOrName: string): Observable<HttpResponse<User[]>> => this.courseManagementService.searchStudents(this.courseId, loginOrName);

    addStudentToPresentation = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());

    removeStudentFromPresentation = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());

    handleAssignedStudentsSizeChange = (filteredAssignedStudentsSize: number): void => this.filteredAssignedStudentsSize.set(filteredAssignedStudentsSize);

    private createFromForm(): PresentationAssessment {
        const formValue = this.editForm.getRawValue();
        return {
            id: this.presentationAssessment?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            maxPoints: formValue.maxPoints ?? undefined,
            resultPoints: formValue.resultPoints ?? undefined,
            presentationDate: formValue.presentationDate ? dayjs(formValue.presentationDate) : undefined,
            courseId: this.courseId,
        };
    }
}
