import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { faArrowUpRightFromSquare, faLink, faPencilAlt, faPlus, faSearch, faTrash, faUsers } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiButtonGroupComponent,
    TumUiDialogComponent,
    TumUiIconFieldComponent,
    TumUiInputDirective,
    TumUiMessageComponent,
    TumUiPaginatorComponent,
    TumUiSelectComponent,
    TumUiTableDirective,
    TumUiTableSortEvent,
    TumUiTableSortableColumnComponent,
    TumUiTagComponent,
} from '@tumaet/ui-angular';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { onError } from 'app/foundation/util/global.utils';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { PresentationAssessmentFormDialogComponent, PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessmentInstanceFormDialogComponent } from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { TranslateService } from '@ngx-translate/core';
import { deepClone } from 'app/foundation/util/deep-clone.util';

type PresentationViewMode = 'presentations' | 'students';
type AssessmentStatusFilter = 'all' | 'assessed' | 'pending';
type PresentationTypeFilter = 'all' | 'standalone' | 'exercise';

interface FilterOption<T> {
    label: string;
    value: T;
}

interface PresentationStudentRow {
    studentLogin: string;
    student: User;
    presentationAssessment: PresentationAssessment;
    instance: PresentationAssessmentInstance;
}

interface SelectedPresentationStudentRow {
    student: User;
    studentLogin: string;
    presentationAssessment: PresentationAssessment;
    instance?: PresentationAssessmentInstance;
}

const presentationSidebarCollapseStateRecord: Record<string, boolean> = { standalone: false, linkedToExercise: false };
const PRESENTATION_SIDEBAR_COLLAPSE_STATE = presentationSidebarCollapseStateRecord as CollapseState;
const presentationSidebarAlwaysShowRecord: Record<string, boolean> = {};
const PRESENTATION_SIDEBAR_ALWAYS_SHOW = presentationSidebarAlwaysShowRecord as SidebarItemShowAlways;

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    styleUrl: './presentation-assessment-management.component.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisDatePipe,
        CourseTitleBarActionsDirective,
        FormsModule,
        ArtemisTranslatePipe,
        RouterLink,
        PresentationAssessmentFormDialogComponent,
        PresentationAssessmentInstanceFormDialogComponent,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiButtonGroupComponent,
        TumUiDialogComponent,
        TumUiIconFieldComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
        TumUiPaginatorComponent,
        TumUiSelectComponent,
        TumUiTableDirective,
        TumUiTableSortableColumnComponent,
        TumUiTagComponent,
        SidebarComponent,
    ],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly translateService = inject(TranslateService);

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faLink = faLink;
    protected readonly faUsers = faUsers;
    protected readonly faSearch = faSearch;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;

    readonly courseId = signal<number>(0);
    readonly course = signal<Course | undefined>(undefined);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    readonly isSaving = signal(false);
    readonly isLoadingAssignedStudents = signal(false);
    readonly exercises = signal<Exercise[]>([]);
    readonly courseStudents = signal<User[]>([]);
    readonly viewMode = signal<PresentationViewMode>('students');
    readonly selectedPresentationId = signal<number | undefined>(undefined);
    readonly studentSearchTerm = signal('');
    readonly assessmentStatusFilter = signal<AssessmentStatusFilter>('all');
    readonly presentationFilter = signal<number | 'all'>('all');
    readonly presentationTypeFilter = signal<PresentationTypeFilter>('all');
    readonly overviewPage = signal(0);
    readonly overviewPageSize = signal(25);
    readonly expandedInstanceIds = signal<number[]>([]);
    readonly expandedStudentRows = signal<string[]>([]);
    readonly presentationDialogVisible = signal(false);
    readonly dialogPresentationAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly instanceDialogVisible = signal(false);
    readonly dialogInstancePresentationAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly dialogInstance = signal<PresentationAssessmentInstance | undefined>(undefined);
    readonly dialogInstanceStudentLogin = signal<string | undefined>(undefined);
    readonly dialogAssignedStudents = signal<User[]>([]);
    readonly studentSortField = signal('studentLogin');
    readonly studentSortOrder = signal(1);
    readonly sidebarCollapseState = PRESENTATION_SIDEBAR_COLLAPSE_STATE;
    readonly sidebarItemAlwaysShow = PRESENTATION_SIDEBAR_ALWAYS_SHOW;
    readonly selectedPresentation = computed(() => {
        const selectedId = this.selectedPresentationId();
        return this.presentationAssessments().find((assessment) => assessment.id === selectedId) ?? this.presentationAssessments()[0];
    });
    readonly studentRows = computed<PresentationStudentRow[]>(() => {
        const studentsByLogin = new Map(
            this.courseStudents()
                .filter((student) => !!student.login)
                .map((student) => [student.login!, student]),
        );
        return this.presentationAssessments().flatMap((presentationAssessment) =>
            (presentationAssessment.instances ?? []).flatMap((instance) =>
                (instance.studentLogins ?? []).map((studentLogin) => ({
                    studentLogin,
                    student: studentsByLogin.get(studentLogin) ?? new User(undefined, studentLogin),
                    presentationAssessment,
                    instance,
                })),
            ),
        );
    });
    readonly selectedInstances = computed(() => this.selectedPresentation()?.instances ?? []);
    readonly selectedPresentationStudentRows = computed<SelectedPresentationStudentRow[]>(() => {
        const presentationAssessment = this.selectedPresentation();
        if (!presentationAssessment) {
            return [];
        }
        const studentsByLogin = new Map(
            this.courseStudents()
                .filter((student) => !!student.login)
                .map((student) => [student.login!, student]),
        );
        return this.selectedInstances().flatMap((instance) =>
            (instance.studentLogins ?? []).map((studentLogin) => ({
                student: studentsByLogin.get(studentLogin) ?? new User(undefined, studentLogin),
                studentLogin,
                presentationAssessment,
                instance,
            })),
        );
    });
    readonly filteredSelectedPresentationStudentRows = computed(() => {
        const query = this.studentSearchTerm().trim().toLocaleLowerCase();
        return query ? this.selectedPresentationStudentRows().filter((row) => row.studentLogin.toLocaleLowerCase().includes(query)) : this.selectedPresentationStudentRows();
    });
    readonly filteredStudentRows = computed(() => {
        const query = this.studentSearchTerm().trim().toLocaleLowerCase();
        const rows = this.studentRows().filter((row) => {
            const matchesQuery =
                !query ||
                [row.studentLogin, row.student.name, row.student.firstName, row.student.lastName, row.student.email, row.presentationAssessment.title]
                    .filter((value): value is string => !!value)
                    .some((value) => value.toLocaleLowerCase().includes(query));
            const matchesStatus =
                this.assessmentStatusFilter() === 'all' ||
                (this.assessmentStatusFilter() === 'assessed' ? this.hasResultPoints(row.instance.resultPoints) : !this.hasResultPoints(row.instance.resultPoints));
            const matchesPresentation = this.presentationFilter() === 'all' || row.presentationAssessment.id === this.presentationFilter();
            const matchesType =
                this.presentationTypeFilter() === 'all' ||
                (this.presentationTypeFilter() === 'exercise' ? !!row.presentationAssessment.exerciseId : !row.presentationAssessment.exerciseId);
            return matchesQuery && matchesStatus && matchesPresentation && matchesType;
        });
        const field = this.studentSortField();
        const order = this.studentSortOrder();
        return [...rows].sort((first, second) => this.compareStudentRows(first, second, field) * order);
    });
    readonly paginatedStudentRows = computed(() => {
        const start = this.overviewPage() * this.overviewPageSize();
        return this.filteredStudentRows().slice(start, start + this.overviewPageSize());
    });
    readonly assessedStudentCount = computed(() => this.studentRows().filter((row) => this.hasResultPoints(row.instance.resultPoints)).length);
    readonly pendingStudentCount = computed(() => this.studentRows().length - this.assessedStudentCount());
    readonly presentationFilterOptions = computed<FilterOption<number | 'all'>[]>(() => [
        { label: this.translateService.instant('artemisApp.presentationAssessment.filter.allPresentations'), value: 'all' },
        ...this.presentationAssessments().map((assessment) => ({ label: assessment.title ?? '-', value: assessment.id! })),
    ]);
    readonly typeFilterOptions = computed<FilterOption<PresentationTypeFilter>[]>(() => [
        { label: this.translateService.instant('artemisApp.presentationAssessment.filter.allTypes'), value: 'all' },
        { label: this.translateService.instant('artemisApp.presentationAssessment.standalone'), value: 'standalone' },
        { label: this.translateService.instant('artemisApp.presentationAssessment.linkedToExercise'), value: 'exercise' },
    ]);
    readonly sidebarData = computed<SidebarData>(() => {
        const overview: SidebarCardElement = {
            id: 'overview',
            title: this.translateService.instant('artemisApp.presentationAssessment.overallOverview'),
            icon: this.faUsers,
            size: 'M',
            active: this.viewMode() === 'students',
            disableNavigation: true,
        };
        const standalonePresentations = this.presentationAssessments()
            .filter((assessment) => !assessment.exerciseId)
            .sort((first, second) => (first.title ?? '').localeCompare(second.title ?? ''));
        const linkedPresentations = this.presentationAssessments()
            .filter((assessment) => !!assessment.exerciseId)
            .sort((first, second) => (first.exerciseTitle ?? '').localeCompare(second.exerciseTitle ?? '') || (first.title ?? '').localeCompare(second.title ?? ''));
        return {
            groupByCategory: true,
            sidebarType: 'default',
            storageId: 'presentationAssessment',
            pinnedData: [overview],
            groupedData: {
                standalone: {
                    entityData: standalonePresentations.map((assessment) => this.toSidebarItem(assessment)),
                    isHideCount: true,
                    translationKey: 'artemisApp.presentationAssessment.standalone',
                },
                linkedToExercise: {
                    entityData: linkedPresentations.map((assessment) => this.toSidebarItem(assessment, true)),
                    isHideCount: true,
                    translationKey: 'artemisApp.presentationAssessment.linkedToExercise',
                },
            },
        };
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
        this.presentationAssessmentService.findCourseStudents(this.courseId()).subscribe({
            next: (res: HttpResponse<User[]>) => this.courseStudents.set(res.body ?? []),
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
        this.viewMode.set('presentations');
    }

    onSidebarItemSelected(itemId: string | number): void {
        if (itemId === 'overview') {
            this.setViewMode('students');
            return;
        }
        const presentationAssessment = this.presentationAssessments().find((assessment) => assessment.id === Number(itemId));
        if (presentationAssessment) {
            this.selectPresentation(presentationAssessment);
        }
    }

    setViewMode(viewMode: PresentationViewMode): void {
        this.viewMode.set(viewMode);
    }

    updateStudentSearch(searchTerm: string): void {
        this.studentSearchTerm.set(searchTerm);
        this.overviewPage.set(0);
    }

    onStudentSort(event: TumUiTableSortEvent): void {
        this.studentSortField.set(event.field);
        this.studentSortOrder.set(event.order);
    }

    getLinkedExerciseRoute(presentationAssessment: PresentationAssessment): (string | number)[] | undefined {
        const exercise = this.exercises().find((candidate) => candidate.id === presentationAssessment.exerciseId);
        if (!exercise?.id || !exercise.type) {
            return undefined;
        }
        return ['/course-management', this.courseId(), `${exercise.type}-exercises`, exercise.id];
    }

    setAssessmentStatusFilter(filter: AssessmentStatusFilter): void {
        this.assessmentStatusFilter.set(filter);
        this.overviewPage.set(0);
    }

    updatePresentationFilter(filter: number | 'all'): void {
        this.presentationFilter.set(filter);
        this.overviewPage.set(0);
    }

    updatePresentationTypeFilter(filter: PresentationTypeFilter): void {
        this.presentationTypeFilter.set(filter);
        this.overviewPage.set(0);
    }

    updateOverviewPageSize(pageSize: number): void {
        this.overviewPageSize.set(pageSize);
        this.overviewPage.set(0);
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

    toggleStudentRowDetails(row: PresentationStudentRow | SelectedPresentationStudentRow): void {
        if (!row.instance) {
            return;
        }
        const key = this.studentRowKey(row);
        this.expandedStudentRows.update((keys) => (keys.includes(key) ? keys.filter((value) => value !== key) : [...keys, key]));
    }

    isStudentRowExpanded(row: PresentationStudentRow | SelectedPresentationStudentRow): boolean {
        return this.expandedStudentRows().includes(this.studentRowKey(row));
    }

    startEdit(presentationAssessment: PresentationAssessment): void {
        this.openPresentationDialog(presentationAssessment);
    }

    startCreateInstance(presentationAssessment: PresentationAssessment): void {
        this.openInstanceDialog(presentationAssessment);
    }

    startEditInstance(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance, studentLogin: string): void {
        this.openInstanceDialog(presentationAssessment, instance, studentLogin);
    }

    deleteInstance(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance, studentLogin: string): void {
        if (!presentationAssessment.id || !instance.id) {
            return;
        }
        const remainingStudentLogins = (instance.studentLogins ?? []).filter((login) => login !== studentLogin);
        const remainingInstance = deepClone(instance);
        remainingInstance.studentLogins = remainingStudentLogins;
        const request: Observable<unknown> = remainingStudentLogins.length
            ? this.presentationAssessmentService.updateInstance(this.courseId(), presentationAssessment.id, remainingInstance)
            : this.presentationAssessmentService.deleteInstance(this.courseId(), presentationAssessment.id, instance.id);
        request.subscribe({
            next: () => this.loadAll(),
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    deletePresentationAssessment(presentationAssessment: PresentationAssessment): void {
        if (!presentationAssessment.id) {
            return;
        }

        this.isSaving.set(true);
        this.presentationAssessmentService
            .delete(this.courseId(), presentationAssessment.id)
            .pipe(finalize(() => this.isSaving.set(false)))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.presentationDialogVisible.set(false);
                    this.presentationAssessments.set(this.presentationAssessments().filter((assessment) => assessment.id !== presentationAssessment.id));
                    this.alertService.success('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    }

    private openPresentationDialog(presentationAssessment?: PresentationAssessment): void {
        this.dialogPresentationAssessment.set(presentationAssessment);
        this.presentationDialogVisible.set(true);
    }

    handlePresentationDialogSave(result: PresentationAssessmentFormDialogResult): void {
        this.isSaving.set(true);
        const presentationAssessment = result.presentationAssessment;
        const isUpdate = Boolean(presentationAssessment.id);
        const request: Observable<unknown> = isUpdate
            ? this.presentationAssessmentService.update(this.courseId(), presentationAssessment)
            : this.presentationAssessmentService.create(this.courseId(), presentationAssessment);

        request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
            next: () => {
                this.presentationDialogVisible.set(false);
                this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
                this.loadAll();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    handlePresentationDialogCancel(): void {
        if (this.isSaving()) {
            return;
        }
        this.presentationDialogVisible.set(false);
    }

    handlePresentationDialogDelete(presentationAssessment: PresentationAssessment): void {
        if (this.isSaving()) {
            return;
        }
        this.deletePresentationAssessment(presentationAssessment);
    }

    private openInstanceDialog(presentationAssessment: PresentationAssessment, instance?: PresentationAssessmentInstance, studentLogin?: string): void {
        const course = this.course();
        if (!presentationAssessment.id || !course) {
            return;
        }
        this.dialogInstancePresentationAssessment.set(presentationAssessment);
        this.dialogInstance.set(instance);
        this.dialogInstanceStudentLogin.set(studentLogin);
        this.dialogAssignedStudents.set(this.resolveStudentsByLogin(studentLogin ? [studentLogin] : (instance?.studentLogins ?? [])));
        this.instanceDialogVisible.set(true);
    }

    private resolveStudentsByLogin(studentLogins: string[]): User[] {
        const studentsByLogin = new Map(
            this.courseStudents()
                .filter((student) => !!student.login)
                .map((student) => [student.login!, student]),
        );
        return studentLogins.map((login) => studentsByLogin.get(login) ?? new User(undefined, login));
    }

    handleInstanceDialogSave(result: PresentationAssessmentInstance): void {
        const presentationAssessment = this.dialogInstancePresentationAssessment();
        if (!presentationAssessment?.id) {
            return;
        }
        this.isSaving.set(true);
        const originalInstance = this.dialogInstance();
        const editedStudentLogin = this.dialogInstanceStudentLogin();
        const originalStudentLogins = originalInstance?.studentLogins ?? [];
        let request: Observable<unknown>;
        if (result.id && originalInstance && editedStudentLogin && originalStudentLogins.length > 1) {
            const remainingInstance = deepClone(originalInstance);
            remainingInstance.studentLogins = originalStudentLogins.filter((login) => login !== editedStudentLogin);
            const editedInstance = deepClone(result);
            editedInstance.id = undefined;
            editedInstance.studentLogins = [editedStudentLogin];
            request = forkJoin([
                this.presentationAssessmentService.updateInstance(this.courseId(), presentationAssessment.id, remainingInstance),
                this.presentationAssessmentService.createInstance(this.courseId(), presentationAssessment.id, editedInstance),
            ]);
        } else if (result.id) {
            request = this.presentationAssessmentService.updateInstance(this.courseId(), presentationAssessment.id, result);
        } else {
            request = forkJoin(
                (result.studentLogins ?? []).map((studentLogin) => {
                    const studentInstance = deepClone(result);
                    studentInstance.studentLogins = [studentLogin];
                    return this.presentationAssessmentService.createInstance(this.courseId(), presentationAssessment.id!, studentInstance);
                }),
            );
        }
        request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
            next: () => {
                this.instanceDialogVisible.set(false);
                this.loadAll();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    handleInstanceDialogCancel(): void {
        if (this.isSaving()) {
            return;
        }
        this.instanceDialogVisible.set(false);
    }

    hasResultPoints(resultPoints: number | null | undefined): resultPoints is number {
        return resultPoints !== undefined && resultPoints !== null;
    }

    studentRowKey(row: PresentationStudentRow | SelectedPresentationStudentRow): string {
        return `${row.instance?.id ?? 'new'}:${row.studentLogin}`;
    }

    private compareStudentRows(first: PresentationStudentRow, second: PresentationStudentRow, field: string): number {
        const firstValue = this.studentSortValue(first, field);
        const secondValue = this.studentSortValue(second, field);
        if (firstValue === secondValue) {
            return 0;
        }
        if (firstValue === undefined) {
            return 1;
        }
        if (secondValue === undefined) {
            return -1;
        }
        return firstValue < secondValue ? -1 : 1;
    }

    private studentSortValue(row: PresentationStudentRow, field: string): string | number | undefined {
        switch (field) {
            case 'studentLogin':
                return row.studentLogin.toLocaleLowerCase();
            case 'presentationTitle':
                return row.presentationAssessment.title?.toLocaleLowerCase();
            case 'presentationDate':
                return row.instance.presentationDate?.valueOf();
            case 'resultPoints':
                return row.instance.resultPoints ?? undefined;
            default:
                return undefined;
        }
    }

    private toSidebarItem(assessment: PresentationAssessment, showExerciseTitle = false): SidebarCardElement {
        return {
            id: assessment.id!,
            title: assessment.title ?? '',
            subtitleLeft: showExerciseTitle ? assessment.exerciseTitle : undefined,
            subtitleLeftIcon: showExerciseTitle ? this.faLink : undefined,
            size: 'M',
            active: this.viewMode() === 'presentations' && this.selectedPresentationId() === assessment.id,
            disableNavigation: true,
        };
    }
}
