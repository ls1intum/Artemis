import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, forkJoin, of } from 'rxjs';
import { finalize, switchMap, tap } from 'rxjs/operators';

import { faPencilAlt, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogService } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';

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
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    imports: [FaIconComponent, TranslateDirective, DeleteButtonDirective, ArtemisDatePipe, CourseTitleBarActionsDirective, ButtonModule, TableModule, MessageModule],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;

    readonly courseId = signal<number>(0);
    readonly course = signal<Course | undefined>(undefined);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    readonly isSaving = signal(false);
    readonly isLoadingAssignedStudents = signal(false);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

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
        this.openPresentationDialog(undefined, []);
    }

    startEdit(presentationAssessment: PresentationAssessment): void {
        if (!presentationAssessment.id) {
            return;
        }

        this.isLoadingAssignedStudents.set(true);
        this.presentationAssessmentService
            .findStudents(this.courseId(), presentationAssessment.id)
            .pipe(finalize(() => this.isLoadingAssignedStudents.set(false)))
            .subscribe({
                next: (res: HttpResponse<User[]>) => this.openPresentationDialog(presentationAssessment, res.body ?? []),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
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
        const dialogRef = this.dialogService.open(PresentationAssessmentFormDialogComponent, {
            header: this.translateService.instant(
                presentationAssessment ? 'artemisApp.presentationAssessment.home.editLabel' : 'artemisApp.presentationAssessment.home.createLabel',
            ),
            width: 'min(80rem, calc(100vw - 2rem))',
            breakpoints: {
                '960px': 'calc(100vw - 2rem)',
                '640px': '100vw',
            },
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: {
                courseId: this.courseId(),
                course: this.course(),
                presentationAssessment,
                assignedStudents,
            },
        });

        dialogRef?.onClose.subscribe((result: PresentationAssessmentFormDialogResult | undefined) => {
            if (result) {
                this.save(result);
            }
        });
    }

    private save(result: PresentationAssessmentFormDialogResult): void {
        this.isSaving.set(true);
        const presentationAssessment = result.presentationAssessment;
        const isUpdate = Boolean(presentationAssessment.id);
        const request = isUpdate
            ? this.presentationAssessmentService.update(this.courseId(), presentationAssessment)
            : this.presentationAssessmentService.create(this.courseId(), presentationAssessment);
        let savedAssessment: PresentationAssessment | null = null;

        request
            .pipe(
                tap((response: HttpResponse<PresentationAssessment>) => (savedAssessment = response.body)),
                switchMap((response: HttpResponse<PresentationAssessment>) =>
                    this.persistAssignedStudents(response.body, result.assignedStudents, result.originalAssignedStudents),
                ),
                finalize(() => this.isSaving.set(false)),
            )
            .subscribe({
                next: () => {
                    this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
                    this.loadAll();
                },
                error: (res: HttpErrorResponse) => {
                    if (savedAssessment) {
                        this.loadAll();
                    }
                    onError(this.alertService, res);
                },
            });
    }

    private persistAssignedStudents(savedAssessment: PresentationAssessment | null, assignedStudents: User[], originalAssignedStudents: User[]) {
        if (!savedAssessment?.id) {
            return of([]);
        }

        const originalLogins = new Set(originalAssignedStudents.map((student) => student.login).filter((login): login is string => !!login));
        const assignedLogins = new Set(assignedStudents.map((student) => student.login).filter((login): login is string => !!login));
        const addRequests = [...assignedLogins]
            .filter((login) => !originalLogins.has(login))
            .map((login) => this.presentationAssessmentService.addStudent(this.courseId(), savedAssessment.id!, login));
        const removeRequests = [...originalLogins]
            .filter((login) => !assignedLogins.has(login))
            .map((login) => this.presentationAssessmentService.removeStudent(this.courseId(), savedAssessment.id!, login));
        const requests = [...addRequests, ...removeRequests];

        return requests.length ? forkJoin(requests) : of([]);
    }
}
