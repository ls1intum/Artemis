import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import dayjs from 'dayjs/esm';

import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { Course, CourseGroup } from 'app/course/shared/entities/course.model';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { User } from 'app/account/user/user.model';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { TranslateService } from '@ngx-translate/core';

const resultPointsDoNotExceedMaxPoints: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const resultPoints = control.get('resultPoints')?.value;
    const maxPoints = control.get('maxPoints')?.value;
    return resultPoints !== null && resultPoints !== undefined && Number(resultPoints) > Number(maxPoints) ? { resultPointsExceedMaxPoints: true } : null;
};

export interface PresentationAssessmentInstanceDialogData {
    courseId: number;
    course: Course;
    presentationAssessment: PresentationAssessment;
    instance?: PresentationAssessmentInstance;
    assignedStudents: User[];
}

@Component({
    selector: 'jhi-presentation-assessment-instance-form-dialog',
    templateUrl: './presentation-assessment-instance-form-dialog.component.html',
    styleUrl: './presentation-assessment-instance-form-dialog.component.scss',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        ButtonModule,
        InputNumberModule,
        MessageModule,
        SelectModule,
        InputTextModule,
        FaIconComponent,
        TranslateDirective,
        FormDateTimePickerComponent,
        CourseGroupComponent,
    ],
})
export class PresentationAssessmentInstanceFormDialogComponent implements OnInit {
    private readonly formBuilder = inject(FormBuilder);
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly translateService = inject(TranslateService);

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly studentsCourseGroup = CourseGroup.STUDENTS;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;
    protected readonly DateTimePickerType = DateTimePickerType;
    protected readonly hiddenStudentColumnFields = ['id', 'visibleRegistrationNumber', 'email'];
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

    private courseId = 0;
    private instance?: PresentationAssessmentInstance;
    private course!: Course;

    readonly presentationStudentCourse = computed(() => {
        const presentationCourse: Course = Object.assign({}, this.course);
        presentationCourse.isAtLeastInstructor = false;
        return presentationCourse;
    });

    editForm = this.formBuilder.group(
        {
            presentationDate: [undefined as dayjs.Dayjs | undefined, Validators.required],
            presentationTime: [undefined as dayjs.Dayjs | undefined],
            resultPoints: [undefined as number | undefined, [Validators.min(0)]],
            maxPoints: [0],
            language: ['en', Validators.required],
            mode: [PresentationAssessmentMode.IN_PERSON, Validators.required],
            location: ['', Validators.maxLength(255)],
            meetingLink: ['', Validators.maxLength(1000)],
        },
        { validators: resultPointsDoNotExceedMaxPoints },
    );

    ngOnInit(): void {
        const data = this.dialogConfig.data as PresentationAssessmentInstanceDialogData;
        this.courseId = data.courseId;
        this.course = data.course;
        this.instance = data.instance;
        this.assignedStudents.set([...data.assignedStudents]);
        this.editForm.reset({
            presentationDate: data.instance?.presentationDate?.startOf('day'),
            presentationTime: data.instance?.presentationDate,
            resultPoints: data.instance?.resultPoints,
            maxPoints: data.presentationAssessment.maxPoints ?? 0,
            language: data.instance?.language ?? 'en',
            mode: data.instance?.mode ?? PresentationAssessmentMode.IN_PERSON,
            location: data.instance?.location ?? '',
            meetingLink: data.instance?.meetingLink ?? '',
        });
    }

    save(): void {
        if (this.editForm.invalid || this.assignedStudents().length === 0) {
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
        this.dialogRef.close({
            id: this.instance?.id,
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
        } satisfies PresentationAssessmentInstance);
    }

    cancel(): void {
        this.dialogRef.close();
    }

    studentSearch = (loginOrName: string): Observable<HttpResponse<User[]>> => this.courseManagementService.searchStudents(this.courseId, loginOrName);
    addStudent = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());
    removeStudent = (): Observable<HttpResponse<void>> => of(new HttpResponse<void>());
    handleAssignedStudentsSizeChange = (size: number): void => this.filteredAssignedStudentsSize.set(size);
}
