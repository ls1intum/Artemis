import { Component, OnInit, TemplateRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import dayjs from 'dayjs/esm';
import { Observable, Subject, forkJoin, of } from 'rxjs';

import { faBan, faPencilAlt, faPlus, faSave, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { onError } from 'app/foundation/util/global.utils';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortService } from 'app/foundation/service/sort.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { Course, CourseGroup } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { User } from 'app/account/user/user.model';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    styleUrl: './presentation-assessment-management.component.scss',
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        DeleteButtonDirective,
        SortDirective,
        SortByDirective,
        ArtemisDatePipe,
        FormDateTimePickerComponent,
        CourseTitleBarActionsDirective,
        CourseGroupComponent,
    ],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly formBuilder = inject(FormBuilder);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly alertService = inject(AlertService);
    private readonly sortService = inject(SortService);
    private readonly modalService = inject(NgbModal);

    protected readonly faBan = faBan;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faSave = faSave;
    protected readonly faSort = faSort;
    protected readonly faTrash = faTrash;
    protected readonly studentsCourseGroup = CourseGroup.STUDENTS;

    readonly courseId = signal<number>(0);
    readonly course = signal<Course | undefined>(undefined);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    readonly assignedStudents = signal<User[]>([]);
    readonly editedAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly isSaving = signal(false);
    readonly isLoadingAssignedStudents = signal(false);
    readonly showForm = signal(false);
    readonly filteredAssignedStudentsSize = signal(0);

    readonly presentationStudentCourse = computed<Course | undefined>(() => {
        const course = this.course();
        if (!course) {
            return undefined;
        }
        const presentationCourse: Course = Object.assign({}, course);
        presentationCourse.isAtLeastInstructor = false;
        return presentationCourse;
    });

    readonly studentExportFilename = computed(() => {
        const title = this.editedAssessment()?.title?.trim();
        return title ? `${title} Students` : 'Presentation Students';
    });

    readonly studentSectionTitle = computed(() => this.editedAssessment()?.title?.trim() || this.editForm.controls.title.value?.trim() || 'New presentation');

    predicate = 'presentationDate';
    ascending = true;
    private modalRef?: NgbModalRef;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    editForm = this.formBuilder.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        description: ['', [Validators.maxLength(1000)]],
        maxPoints: [0, [Validators.required, Validators.min(0.01)]],
        resultPoints: [undefined as number | undefined, [Validators.min(0)]],
        presentationDate: [undefined as dayjs.Dayjs | undefined],
    });

    ngOnInit(): void {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId') ?? this.route.parent?.snapshot.paramMap.get('courseId')));
        this.route.parent?.data.subscribe(({ course }) => this.course.set(course));
        this.loadAll();
    }

    loadAll(): void {
        this.presentationAssessmentService.findAllByCourseId(this.courseId()).subscribe({
            next: (res: HttpResponse<PresentationAssessment[]>) => {
                this.presentationAssessments.set(res.body ?? []);
                this.sortRows();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    startCreate(content: TemplateRef<unknown>): void {
        this.editedAssessment.set(undefined);
        this.assignedStudents.set([]);
        this.editForm.reset({
            title: '',
            description: '',
            maxPoints: 20,
            resultPoints: undefined,
            presentationDate: undefined,
        });
        this.showForm.set(true);
        this.openPresentationModal(content);
    }

    startEdit(presentationAssessment: PresentationAssessment, content: TemplateRef<unknown>): void {
        this.editedAssessment.set(presentationAssessment);
        this.loadAssignedStudents(presentationAssessment);
        this.editForm.reset({
            title: presentationAssessment.title ?? '',
            description: presentationAssessment.description ?? '',
            maxPoints: presentationAssessment.maxPoints ?? 0,
            resultPoints: presentationAssessment.resultPoints,
            presentationDate: presentationAssessment.presentationDate,
        });
        this.showForm.set(true);
        this.openPresentationModal(content);
    }

    cancelEdit(): void {
        this.modalRef?.dismiss();
        this.showForm.set(false);
        this.editedAssessment.set(undefined);
        this.assignedStudents.set([]);
        this.editForm.reset();
    }

    save(): void {
        if (this.editForm.invalid) {
            this.editForm.markAllAsTouched();
            return;
        }

        this.isSaving.set(true);
        const presentationAssessment = this.createFromForm();
        const request = presentationAssessment.id
            ? this.presentationAssessmentService.update(this.courseId(), presentationAssessment)
            : this.presentationAssessmentService.create(this.courseId(), presentationAssessment);

        request.subscribe({
            next: (res: HttpResponse<PresentationAssessment>) => this.onSaveSuccess(Boolean(presentationAssessment.id), res.body),
            error: (res: HttpErrorResponse) => this.onSaveError(res),
        });
    }

    deletePresentationAssessment(presentationAssessment: PresentationAssessment): void {
        if (!presentationAssessment.id) {
            return;
        }

        this.presentationAssessmentService.delete(this.courseId(), presentationAssessment.id).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.presentationAssessments.set(this.presentationAssessments().filter((assessment) => assessment.id !== presentationAssessment.id));
                this.alertService.success('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    sortRows(): void {
        const sortedAssessments = [...this.presentationAssessments()];
        this.sortService.sortByProperty(sortedAssessments, this.predicate, this.ascending);
        this.presentationAssessments.set(sortedAssessments);
    }

    studentSearch = (loginOrName: string): Observable<HttpResponse<User[]>> => this.courseManagementService.searchStudents(this.courseId(), loginOrName);

    addStudentToPresentation = (login: string): Observable<HttpResponse<void>> => {
        const assessmentId = this.editedAssessment()?.id;
        if (!assessmentId) {
            return of(new HttpResponse<void>());
        }
        return this.presentationAssessmentService.addStudent(this.courseId(), assessmentId, login);
    };

    removeStudentFromPresentation = (login: string): Observable<HttpResponse<void>> => {
        const assessmentId = this.editedAssessment()?.id;
        if (!assessmentId) {
            return of(new HttpResponse<void>());
        }
        return this.presentationAssessmentService.removeStudent(this.courseId(), assessmentId, login);
    };

    handleAssignedStudentsSizeChange = (filteredAssignedStudentsSize: number): void => this.filteredAssignedStudentsSize.set(filteredAssignedStudentsSize);

    loadAssignedStudents(presentationAssessment: PresentationAssessment): void {
        if (!presentationAssessment.id) {
            this.assignedStudents.set([]);
            return;
        }

        this.isLoadingAssignedStudents.set(true);
        this.presentationAssessmentService.findStudents(this.courseId(), presentationAssessment.id).subscribe({
            next: (res: HttpResponse<User[]>) => {
                this.assignedStudents.set(res.body ?? []);
                this.isLoadingAssignedStudents.set(false);
            },
            error: (res: HttpErrorResponse) => {
                this.isLoadingAssignedStudents.set(false);
                onError(this.alertService, res);
            },
        });
    }

    private createFromForm(): PresentationAssessment {
        const formValue = this.editForm.getRawValue();
        return {
            id: this.editedAssessment()?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            maxPoints: formValue.maxPoints ?? undefined,
            resultPoints: formValue.resultPoints ?? undefined,
            presentationDate: formValue.presentationDate ? dayjs(formValue.presentationDate) : undefined,
            courseId: this.courseId(),
        };
    }

    private onSaveSuccess(isUpdate: boolean, savedAssessment?: PresentationAssessment | null): void {
        if (!isUpdate && savedAssessment?.id) {
            this.persistAssignedStudents(savedAssessment);
            return;
        }

        this.finishSave(isUpdate);
    }

    private onSaveError(error: HttpErrorResponse): void {
        this.isSaving.set(false);
        onError(this.alertService, error);
    }

    private persistAssignedStudents(savedAssessment: PresentationAssessment): void {
        const assignedStudentLogins = this.assignedStudents()
            .map((student) => student.login)
            .filter((login): login is string => !!login);

        const requests = [...new Set(assignedStudentLogins)].map((login) => this.presentationAssessmentService.addStudent(this.courseId(), savedAssessment.id!, login));

        if (!requests.length) {
            this.editedAssessment.set(savedAssessment);
            this.finishSave(false);
            return;
        }

        forkJoin(requests).subscribe({
            next: () => {
                this.editedAssessment.set(savedAssessment);
                this.finishSave(false);
            },
            error: (res: HttpErrorResponse) => {
                this.editedAssessment.set(savedAssessment);
                this.onSaveError(res);
            },
        });
    }

    private finishSave(isUpdate: boolean, closeForm = true): void {
        this.isSaving.set(false);
        this.showForm.set(!closeForm);
        if (closeForm) {
            this.editedAssessment.set(undefined);
            this.assignedStudents.set([]);
            this.modalRef?.close();
        }
        this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
        this.loadAll();
    }

    private openPresentationModal(content: TemplateRef<unknown>): void {
        this.modalRef = this.modalService.open(content, { size: 'xl', backdrop: 'static', scrollable: true });
    }
}
