import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { faPencilAlt, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { onError } from 'app/foundation/util/global.utils';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { PresentationAssessmentFormDialogComponent, PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiMessageComponent, TumUiTableDirective, TumUiTableSortEvent, TumUiTableSortableColumnComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    imports: [
        FaIconComponent,
        TranslateDirective,
        DeleteButtonDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CourseTitleBarActionsDirective,
        PresentationAssessmentFormDialogComponent,
        TumUiButtonComponent,
        TumUiDialogComponent,
        TumUiMessageComponent,
        TumUiTableDirective,
        TumUiTableSortableColumnComponent,
    ],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;

    readonly courseId = signal<number>(0);
    readonly course = signal<Course | undefined>(undefined);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    readonly isSaving = signal(false);
    readonly isLoadingAssignedStudents = signal(false);
    readonly dialogVisible = signal(false);
    readonly dialogPresentationAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly dialogAssignedStudents = signal<User[]>([]);
    readonly sortField = signal('presentationDate');
    readonly sortOrder = signal(1);

    readonly sortedPresentationAssessments = computed(() => {
        const field = this.sortField();
        const order = this.sortOrder();
        return [...this.presentationAssessments()].sort((first, second) => this.compareAssessments(first, second, field) * order);
    });

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private dialogRequestId = 0;

    ngOnInit(): void {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId') ?? this.route.parent?.snapshot.paramMap.get('courseId')));
        this.route.parent?.data.subscribe(({ course }) => this.course.set(course));
        this.loadAll();
    }

    loadAll(): void {
        this.presentationAssessmentService.findAllByCourseId(this.courseId()).subscribe({
            next: (res: HttpResponse<PresentationAssessment[]>) => this.presentationAssessments.set(res.body ?? []),
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    startCreate(): void {
        if (this.isSaving()) {
            return;
        }
        this.dialogRequestId++;
        this.isLoadingAssignedStudents.set(false);
        this.openPresentationDialog(undefined, []);
    }

    startEdit(presentationAssessment: PresentationAssessment): void {
        if (this.isSaving()) {
            return;
        }
        if (!presentationAssessment.id) {
            return;
        }

        const requestId = ++this.dialogRequestId;
        this.isLoadingAssignedStudents.set(true);
        this.presentationAssessmentService
            .findStudents(this.courseId(), presentationAssessment.id)
            .pipe(
                finalize(() => {
                    if (requestId === this.dialogRequestId) {
                        this.isLoadingAssignedStudents.set(false);
                    }
                }),
            )
            .subscribe({
                next: (res: HttpResponse<User[]>) => {
                    if (requestId === this.dialogRequestId) {
                        this.openPresentationDialog(presentationAssessment, res.body ?? []);
                    }
                },
                error: (res: HttpErrorResponse) => {
                    if (requestId === this.dialogRequestId) {
                        onError(this.alertService, res);
                    }
                },
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

    private openPresentationDialog(presentationAssessment: PresentationAssessment | undefined, assignedStudents: User[]): void {
        this.dialogPresentationAssessment.set(presentationAssessment);
        this.dialogAssignedStudents.set(assignedStudents);
        this.dialogVisible.set(true);
    }

    handleDialogSave(result: PresentationAssessmentFormDialogResult): void {
        this.dialogRequestId++;
        this.save(result);
    }

    handleDialogCancel(): void {
        if (this.isSaving()) {
            return;
        }
        this.dialogRequestId++;
        this.isLoadingAssignedStudents.set(false);
        this.dialogVisible.set(false);
    }

    handleDialogVisibleChange(visible: boolean): void {
        if (!visible && this.isSaving()) {
            this.dialogVisible.set(true);
            return;
        }
        if (!visible) {
            this.handleDialogCancel();
            return;
        }
        this.dialogVisible.set(true);
    }

    onSort(event: TumUiTableSortEvent): void {
        this.sortField.set(event.field);
        this.sortOrder.set(event.order);
    }

    private save(result: PresentationAssessmentFormDialogResult): void {
        if (this.isSaving()) {
            return;
        }

        this.isSaving.set(true);
        const requestId = this.dialogRequestId;
        const formAssessment = result.presentationAssessment;
        const presentationAssessment = {
            id: formAssessment.id,
            title: formAssessment.title,
            description: formAssessment.description,
            maxPoints: formAssessment.maxPoints,
            resultPoints: formAssessment.resultPoints,
            presentationDate: formAssessment.presentationDate,
            courseId: formAssessment.courseId,
            studentLogins: [...new Set(result.assignedStudents.map((student) => student.login).filter((login): login is string => !!login))],
        };
        const isUpdate = Boolean(presentationAssessment.id);
        const request = isUpdate
            ? this.presentationAssessmentService.update(this.courseId(), presentationAssessment)
            : this.presentationAssessmentService.create(this.courseId(), presentationAssessment);

        request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
            next: () => {
                if (requestId === this.dialogRequestId) {
                    this.dialogVisible.set(false);
                    this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
                    this.loadAll();
                }
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    private compareAssessments(first: PresentationAssessment, second: PresentationAssessment, field: string): number {
        const firstValue = this.sortValue(first, field);
        const secondValue = this.sortValue(second, field);
        if (firstValue === secondValue) {
            return 0;
        }
        if (firstValue === undefined || firstValue === null) {
            return 1;
        }
        if (secondValue === undefined || secondValue === null) {
            return -1;
        }
        return firstValue < secondValue ? -1 : 1;
    }

    private sortValue(presentationAssessment: PresentationAssessment, field: string): string | number | undefined {
        switch (field) {
            case 'presentationDate':
                return presentationAssessment.presentationDate?.valueOf();
            case 'title':
                return presentationAssessment.title?.toLowerCase();
            case 'maxPoints':
                return presentationAssessment.maxPoints;
            case 'resultPoints':
                return presentationAssessment.resultPoints;
            default:
                return presentationAssessment.description?.toLowerCase();
        }
    }
}
