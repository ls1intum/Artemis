import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { faList, faPencilAlt, faPlus, faSearch, faTrash, faUsers } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogService } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { onError } from 'app/foundation/util/global.utils';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { PresentationAssessmentFormDialogComponent, PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessmentInstanceFormDialogComponent } from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import dayjs from 'dayjs/esm';

type PresentationViewMode = 'presentations' | 'students';
type InstanceTimeFilter = 'all' | 'future';

interface PresentationStudentRow {
    studentLogin: string;
    presentationAssessment: PresentationAssessment;
    instance: PresentationAssessmentInstance;
}

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    styleUrl: './presentation-assessment-management.component.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        DeleteButtonDirective,
        ArtemisDatePipe,
        CourseTitleBarActionsDirective,
        ButtonModule,
        TableModule,
        MessageModule,
        FormsModule,
        InputTextModule,
        ArtemisTranslatePipe,
        RouterLink,
    ],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);
    private readonly courseManagementService = inject(CourseManagementService);

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faList = faList;
    protected readonly faUsers = faUsers;
    protected readonly faSearch = faSearch;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;

    readonly courseId = signal<number>(0);
    readonly course = signal<Course | undefined>(undefined);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    readonly isSaving = signal(false);
    readonly isLoadingAssignedStudents = signal(false);
    readonly exercises = signal<Exercise[]>([]);
    readonly viewMode = signal<PresentationViewMode>('presentations');
    readonly selectedPresentationId = signal<number | undefined>(undefined);
    readonly studentSearchTerm = signal('');
    readonly instanceTimeFilter = signal<InstanceTimeFilter>('all');
    readonly expandedInstanceIds = signal<number[]>([]);
    readonly expandedStudentRows = signal<string[]>([]);
    readonly selectedPresentation = computed(() => {
        const selectedId = this.selectedPresentationId();
        return this.presentationAssessments().find((assessment) => assessment.id === selectedId) ?? this.presentationAssessments()[0];
    });
    readonly studentRows = computed<PresentationStudentRow[]>(() =>
        this.presentationAssessments().flatMap((presentationAssessment) =>
            this.filterInstances(presentationAssessment.instances ?? []).flatMap((instance) =>
                (instance.studentLogins ?? []).map((studentLogin) => ({ studentLogin, presentationAssessment, instance })),
            ),
        ),
    );
    readonly selectedInstances = computed(() => this.filterInstances(this.selectedPresentation()?.instances ?? []));
    readonly filteredStudentRows = computed(() => {
        const query = this.studentSearchTerm().trim().toLocaleLowerCase();
        return query ? this.studentRows().filter((row) => row.studentLogin.toLocaleLowerCase().includes(query)) : this.studentRows();
    });

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit(): void {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId') ?? this.route.parent?.snapshot.paramMap.get('courseId')));
        this.route.parent?.data.subscribe(({ course }) => this.course.set(course));
        this.loadAll();
        this.courseManagementService.findWithExercises(this.courseId()).subscribe({
            next: (res: HttpResponse<Course>) => this.exercises.set(res.body?.exercises ?? []),
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    loadAll(): void {
        this.presentationAssessmentService.findAllByCourseId(this.courseId()).subscribe({
            next: (res: HttpResponse<PresentationAssessment[]>) => {
                const assessments = res.body ?? [];
                this.presentationAssessments.set(assessments);
                if (!assessments.some((assessment) => assessment.id === this.selectedPresentationId())) {
                    this.selectedPresentationId.set(assessments[0]?.id);
                }
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    startCreate(): void {
        this.openPresentationDialog();
    }

    selectPresentation(presentationAssessment: PresentationAssessment): void {
        this.selectedPresentationId.set(presentationAssessment.id);
    }

    setViewMode(viewMode: PresentationViewMode): void {
        this.viewMode.set(viewMode);
    }

    updateStudentSearch(searchTerm: string): void {
        this.studentSearchTerm.set(searchTerm);
    }

    getLinkedExerciseRoute(presentationAssessment: PresentationAssessment): (string | number)[] | undefined {
        const exercise = this.exercises().find((candidate) => candidate.id === presentationAssessment.exerciseId);
        if (!exercise?.id || !exercise.type) {
            return undefined;
        }
        return ['/course-management', this.courseId(), `${exercise.type}-exercises`, exercise.id];
    }

    setInstanceTimeFilter(filter: InstanceTimeFilter): void {
        this.instanceTimeFilter.set(filter);
    }

    toggleInstanceDetails(instance: PresentationAssessmentInstance): void {
        if (!instance.id) {
            return;
        }
        this.expandedInstanceIds.update((ids) => (ids.includes(instance.id!) ? ids.filter((id) => id !== instance.id) : [...ids, instance.id!]));
    }

    isInstanceExpanded(instance: PresentationAssessmentInstance): boolean {
        return !!instance.id && this.expandedInstanceIds().includes(instance.id);
    }

    toggleStudentRowDetails(row: PresentationStudentRow): void {
        const key = this.studentRowKey(row);
        this.expandedStudentRows.update((keys) => (keys.includes(key) ? keys.filter((value) => value !== key) : [...keys, key]));
    }

    isStudentRowExpanded(row: PresentationStudentRow): boolean {
        return this.expandedStudentRows().includes(this.studentRowKey(row));
    }

    startEdit(presentationAssessment: PresentationAssessment): void {
        this.openPresentationDialog(presentationAssessment);
    }

    startCreateInstance(presentationAssessment: PresentationAssessment): void {
        this.openInstanceDialog(presentationAssessment);
    }

    startEditInstance(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance): void {
        this.openInstanceDialog(presentationAssessment, instance);
    }

    deleteInstance(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance): void {
        if (!presentationAssessment.id || !instance.id) {
            return;
        }
        this.presentationAssessmentService.deleteInstance(this.courseId(), presentationAssessment.id, instance.id).subscribe({
            next: () => this.loadAll(),
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

    private openPresentationDialog(presentationAssessment?: PresentationAssessment): void {
        const dialogRef = this.dialogService.open(PresentationAssessmentFormDialogComponent, {
            header: this.translateService.instant(
                presentationAssessment ? 'artemisApp.presentationAssessment.home.editLabel' : 'artemisApp.presentationAssessment.home.createLabel',
            ),
            width: 'min(48rem, calc(100vw - 2rem))',
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
                exercises: this.exercises(),
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

        request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
            next: () => {
                this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
                this.loadAll();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    private openInstanceDialog(presentationAssessment: PresentationAssessment, instance?: PresentationAssessmentInstance): void {
        const course = this.course();
        if (!presentationAssessment.id || !course) {
            return;
        }
        const dialogRef = this.dialogService.open(PresentationAssessmentInstanceFormDialogComponent, {
            header: this.translateService.instant(instance ? 'artemisApp.presentationAssessment.instance.editLabel' : 'artemisApp.presentationAssessment.instance.createLabel'),
            width: 'min(70rem, calc(100vw - 2rem))',
            modal: true,
            draggable: false,
            data: {
                courseId: this.courseId(),
                course,
                presentationAssessment,
                instance,
                assignedStudents: (instance?.studentLogins ?? []).map((login) => {
                    const user = new User(undefined, login);
                    return user;
                }),
            },
        });
        dialogRef?.onClose.subscribe((result: PresentationAssessmentInstance | undefined) => {
            if (!result) {
                return;
            }
            this.isSaving.set(true);
            const request = result.id
                ? this.presentationAssessmentService.updateInstance(this.courseId(), presentationAssessment.id!, result)
                : this.presentationAssessmentService.createInstance(this.courseId(), presentationAssessment.id!, result);
            request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
                next: () => this.loadAll(),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        });
    }

    private filterInstances(instances: PresentationAssessmentInstance[]): PresentationAssessmentInstance[] {
        if (this.instanceTimeFilter() === 'all') {
            return instances;
        }
        const now = dayjs();
        return instances.filter((instance) => !!instance.presentationDate && !instance.presentationDate.isBefore(now));
    }

    private studentRowKey(row: PresentationStudentRow): string {
        return `${row.instance.id ?? 'new'}:${row.studentLogin}`;
    }
}
