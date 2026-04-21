import { Component, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { Subject, Subscription } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ParticipationService } from './participation.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AlertService } from 'app/shared/service/alert.service';
import { faCheck, faCircleNotch, faEraser, faFilePowerpoint, faPencil, faTable, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/programming/shared/actions/instructor-submission-state/programming-exercise-instructor-submission-state.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TableLazyLoadEvent } from 'primeng/table';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared/table-view/table-view';
import { ParticipationManagementDTO } from './participation-management-dto.model';
import { ParticipationSearch } from 'app/shared/table/pageable-table';
import { FilterDropdownComponent } from 'app/exercise/shared/filter-dropdown/filter-dropdown.component';
import { TeamStudentsListComponent } from 'app/exercise/team/team-participate/team-students-list.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';

export enum FilterProp {
    ALL = 'All',
    FAILED = 'Failed',
    NO_SUBMISSIONS = 'NoSubmissions',
    NO_PRACTICE = 'NoPracticeMode',
}

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        RouterLink,
        FaIconComponent,
        TableViewComponent,
        CodeButtonComponent,
        FormDateTimePickerComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        DeleteButtonDirective,
        FeatureToggleDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        FilterDropdownComponent,
        TeamStudentsListComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
    ],
})
export class ParticipationComponent implements OnInit, OnDestroy {
    private readonly route = inject(ActivatedRoute);
    private readonly participationService = inject(ParticipationService);
    private readonly alertService = inject(AlertService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly programmingSubmissionService = inject(ProgrammingSubmissionService);
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly gradingService = inject(GradingService);
    private readonly websocketService = inject(WebsocketService);

    protected readonly faTable = faTable;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faEraser = faEraser;
    protected readonly faFilePowerpoint = faFilePowerpoint;
    protected readonly faPencil = faPencil;
    protected readonly faCheck = faCheck;

    protected FilterProp = FilterProp;

    protected readonly ExerciseType = ExerciseType;
    protected readonly ActionType = ActionType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly RepositoryType = RepositoryType;

    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly participations = signal<ParticipationManagementDTO[]>([]);
    readonly totalRows = signal(0);
    readonly isLoading = signal(false);
    readonly isSaving = signal(false);
    readonly afterDueDate = signal(false);
    readonly activeFilter = signal<FilterProp>(FilterProp.ALL);
    readonly hasLoadedPendingSubmissions = signal(false);
    readonly isAdmin = signal(false);
    readonly isLocalCIEnabled = signal(true);
    readonly gradeStepsDTO = signal<GradeStepsDTO | undefined>(undefined);

    readonly basicPresentationEnabled = computed(() => {
        const ex = this.exercise();
        return !!(ex?.isAtLeastTutor === true && (ex?.course?.presentationScore ?? 0) > 0 && ex?.presentationScoreEnabled === true);
    });

    readonly gradedPresentationEnabled = computed(() => {
        const ex = this.exercise();
        return !!(ex?.course && ex?.isAtLeastTutor && (this.gradeStepsDTO()?.presentationsNumber ?? 0) > 0 && ex?.presentationScoreEnabled === true);
    });

    readonly scoresRoute = computed<any[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];
        const exam = ex.exerciseGroup?.exam;
        const base: any[] = ['/course-management'];
        if (exam) {
            base.push(exam.course!.id, 'exams', exam.id, 'exercise-groups', ex.exerciseGroup!.id);
        } else {
            base.push(ex.course!.id);
        }
        base.push(ex.type + '-exercises', ex.id, 'scores');
        return base;
    });

    readonly relevantFilters = computed<string[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];
        return Object.values(FilterProp).filter((f) => {
            switch (f) {
                case FilterProp.FAILED:
                case FilterProp.NO_PRACTICE:
                    return ex.type === ExerciseType.PROGRAMMING;
                default:
                    return true;
            }
        });
    });

    readonly isExamExercise = computed(() => !!this.exercise()?.exerciseGroup);

    private lastLazyEvent: TableLazyLoadEvent | undefined;
    private currentLoadRequestId = 0;
    // private exerciseSubmissionState: ExerciseSubmissionState = {};
    private paramSub: Subscription;
    private gradeStepsDTOSub: Subscription;
    private websocketSubscriptions: Subscription[] = [];
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    // Track graded presentation edits (not signals — not rendered directly)
    participationsChangedPresentation = new Map<number, ParticipationManagementDTO>();

    // Track individual due date inline editing
    readonly editingDueDateIds = signal<ReadonlySet<number>>(new Set());
    private readonly pendingDueDates = new Map<number, dayjs.Dayjs | undefined>();

    readonly tableOptions = computed<TableViewOptions>(() => ({
        dataKey: 'participationId',
        striped: true,
        scrollable: true,
        scrollHeight: 'flex',
        searchPlaceholder: this.exercise()?.teamMode ? 'artemisApp.exercise.searchForTeams' : 'artemisApp.exercise.searchForStudents',
    }));

    // Template refs
    readonly idCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('idCellTemplate');
    readonly repositoryCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('repositoryCellTemplate');
    readonly initStateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('initStateCellTemplate');
    readonly initDateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('initDateCellTemplate');
    readonly submissionCountCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('submissionCountCellTemplate');
    readonly participantNameCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('participantNameCellTemplate');
    readonly teamStudentsCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('teamStudentsCellTemplate');
    readonly practiceCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('practiceCellTemplate');
    readonly basicPresentationCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('basicPresentationCellTemplate');
    readonly gradedPresentationCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('gradedPresentationCellTemplate');
    readonly individualDueDateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('individualDueDateCellTemplate');

    readonly columns = computed<ColumnDef<ParticipationManagementDTO>[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];

        const cols: ColumnDef<ParticipationManagementDTO>[] = [
            {
                headerKey: 'global.field.id',
                field: 'participationId',
                width: '80px',
                sort: true,
                templateRef: this.idCellTemplate(),
            },
        ];

        if (ex.type === ExerciseType.PROGRAMMING) {
            cols.push({
                headerKey: 'artemisApp.participation.repository',
                width: '80px',
                sort: false,
                templateRef: this.repositoryCellTemplate(),
            });
        }

        cols.push(
            {
                headerKey: 'artemisApp.participation.initializationState',
                field: 'initializationState',
                width: '120px',
                sort: true,
                templateRef: this.initStateCellTemplate(),
            },
            {
                headerKey: 'artemisApp.participation.initializationDate',
                field: 'initializationDate',
                width: '160px',
                sort: true,
                templateRef: this.initDateCellTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.submissionCount',
                field: 'submissionCount',
                width: '100px',
                sort: true,
                templateRef: this.submissionCountCellTemplate(),
            },
        );

        if (!ex.teamMode) {
            cols.push({
                headerKey: 'artemisApp.participation.student',
                field: 'participantName',
                width: '140px',
                sort: true,
                templateRef: this.participantNameCellTemplate(),
            });
        } else {
            cols.push(
                {
                    headerKey: 'artemisApp.participation.team',
                    field: 'participantName',
                    width: '120px',
                    sort: true,
                    templateRef: this.participantNameCellTemplate(),
                },
                {
                    headerKey: 'artemisApp.participation.students',
                    width: '280px',
                    sort: false,
                    templateRef: this.teamStudentsCellTemplate(),
                },
            );
        }

        if (ex.type === ExerciseType.PROGRAMMING && this.afterDueDate()) {
            cols.push({
                headerKey: 'artemisApp.participation.practice',
                field: 'testRun',
                width: '90px',
                sort: true,
                templateRef: this.practiceCellTemplate(),
            });
        }

        if (this.basicPresentationEnabled()) {
            cols.push({
                headerKey: 'artemisApp.participation.presentationScore',
                field: 'presentationScore',
                width: '130px',
                sort: true,
                templateRef: this.basicPresentationCellTemplate(),
            });
        }

        if (this.gradedPresentationEnabled()) {
            cols.push({
                headerKey: 'artemisApp.participation.presentationGrade',
                field: 'presentationScore',
                width: '130px',
                sort: true,
                templateRef: this.gradedPresentationCellTemplate(),
            });
        }

        if (ex.type !== ExerciseType.QUIZ && ex.dueDate) {
            cols.push({
                headerKey: 'artemisApp.participation.individualDueDate',
                field: 'individualDueDate',
                width: '260px',
                sort: true,
                templateRef: this.individualDueDateCellTemplate(),
            });
        }

        return cols;
    });

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => this.loadExercise(+params['exerciseId']));
        this.isAdmin.set(this.accountService.isAdmin());
        this.isLocalCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
    }

    ngOnDestroy() {
        const ex = this.exercise();
        if (ex) {
            this.programmingSubmissionService.unsubscribeAllWebsocketTopics(ex);
        }
        this.dialogErrorSource.unsubscribe();
        this.paramSub?.unsubscribe();
        this.gradeStepsDTOSub?.unsubscribe();
        this.websocketSubscriptions.forEach((sub) => sub.unsubscribe());
    }

    private loadExercise(exerciseId: number) {
        this.isLoading.set(true);
        this.exerciseService.find(exerciseId).subscribe((exerciseResponse) => {
            const ex = exerciseResponse.body!;
            this.exercise.set(ex);
            this.afterDueDate.set(!!ex.dueDate && dayjs().isAfter(ex.dueDate));
            this.loadGradingScale(ex.course?.id);
            if (ex.type === ExerciseType.PROGRAMMING) {
                this.loadSubmissionState(exerciseId);
                this.setupFailedFilterWebsocket(exerciseId);
            }
            this.isLoading.set(false);
        });
    }

    private loadGradingScale(courseId?: number) {
        if (courseId) {
            this.gradeStepsDTOSub = this.gradingService.findGradeStepsForCourse(courseId).subscribe((gradeStepsDTO) => {
                if (gradeStepsDTO.body) {
                    this.gradeStepsDTO.set(gradeStepsDTO.body);
                }
            });
        }
    }

    private loadSubmissionState(exerciseId: number) {
        this.programmingSubmissionService
            .getSubmissionStateOfExercise(exerciseId)
            .pipe()
            .subscribe(() => this.hasLoadedPendingSubmissions.set(true));
    }

    private setupFailedFilterWebsocket(exerciseId: number) {
        const reloadIfFailedFilterActive = () => {
            if (this.activeFilter() === FilterProp.FAILED) {
                this.loadPage();
            }
        };
        const submissionSub = this.websocketService.subscribe(`/topic/exercise/${exerciseId}/newSubmissions`).subscribe(() => reloadIfFailedFilterActive());
        const resultSub = this.websocketService.subscribe(`/topic/exercise/${exerciseId}/newResults`).subscribe(() => reloadIfFailedFilterActive());
        this.websocketSubscriptions.push(submissionSub, resultSub);
    }

    onLazyLoad(event: TableLazyLoadEvent) {
        this.lastLazyEvent = event;
        this.loadPage();
    }

    private loadPage() {
        const ex = this.exercise();
        if (!ex?.id || !this.lastLazyEvent) return;

        this.isLoading.set(true);
        const requestId = ++this.currentLoadRequestId;
        const base = buildDbQueryFromLazyEvent(this.lastLazyEvent);
        const search: ParticipationSearch = {
            ...base,
            filterProp: this.activeFilter() !== FilterProp.ALL ? this.activeFilter() : undefined,
        };

        this.participationService.searchParticipations(ex.id, search).subscribe({
            next: (result) => {
                if (requestId === this.currentLoadRequestId) {
                    this.participations.set(result.content);
                    this.totalRows.set(result.totalElements);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (requestId === this.currentLoadRequestId) {
                    this.alertService.error('artemisApp.participation.loadError');
                    this.isLoading.set(false);
                }
            },
            complete: () => {
                if (requestId === this.currentLoadRequestId) {
                    this.isLoading.set(false);
                }
            },
        });
    }

    updateParticipationFilter(newValue: string) {
        this.activeFilter.set(newValue as FilterProp);
        this.loadPage();
    }

    getParticipationLink(participationId: number): string[] {
        return this.isExamExercise() ? [participationId.toString()] : [participationId.toString(), 'submissions'];
    }

    toProgrammingParticipation(dto: ParticipationManagementDTO): ProgrammingExerciseStudentParticipation {
        const p = new ProgrammingExerciseStudentParticipation();
        p.id = dto.participationId;
        p.initializationState = dto.initializationState as InitializationState | undefined;
        p.initializationDate = dto.initializationDate;
        p.individualDueDate = dto.individualDueDate;
        p.presentationScore = dto.presentationScore;
        p.submissionCount = dto.submissionCount;
        p.participantName = dto.participantName;
        p.participantIdentifier = dto.participantIdentifier;
        p.testRun = dto.testRun;
        p.buildPlanId = dto.buildPlanId;
        p.repositoryUri = dto.repositoryUri;
        if (dto.studentId !== undefined || dto.studentLogin !== undefined) {
            p.student = { id: dto.studentId, login: dto.studentLogin } as User;
        }
        if (dto.teamId !== undefined) {
            p.team = { id: dto.teamId } as Team;
        }
        if (dto.lastResultIsManual !== undefined) {
            const result = new Result();
            result.assessmentType = dto.lastResultIsManual ? AssessmentType.MANUAL : AssessmentType.AUTOMATIC;
            const submission: Submission = { results: [result] };
            p.submissions = [submission];
        }
        return p;
    }

    private toStudentParticipation(dto: ParticipationManagementDTO): StudentParticipation {
        const p = new StudentParticipation();
        p.id = dto.participationId;
        p.presentationScore = dto.presentationScore;
        p.individualDueDate = dto.individualDueDate;
        p.testRun = dto.testRun;
        return p;
    }

    addBasicPresentation(dto: ParticipationManagementDTO) {
        if (!this.basicPresentationEnabled()) return;
        const p = this.toStudentParticipation(dto);
        p.presentationScore = 1;
        this.participationService.update(this.exercise()!, p).subscribe({
            next: () => this.loadPage(),
            error: () => this.alertService.error('artemisApp.participation.addPresentation.error'),
        });
    }

    addGradedPresentation(dto: ParticipationManagementDTO) {
        if (!this.gradedPresentationEnabled() || (dto.presentationScore ?? 0) > 100 || (dto.presentationScore ?? 0) < 0) return;
        this.participationService.update(this.exercise()!, this.toStudentParticipation(dto)).subscribe({
            error: (res: HttpErrorResponse) => {
                const error = res.error;
                if (error?.errorKey === 'invalid.presentations.maxNumberOfPresentationsExceeded') {
                    dto.presentationScore = undefined;
                } else {
                    this.alertService.error('artemisApp.participation.savePresentation.error');
                }
            },
            complete: () => {
                this.participationsChangedPresentation.delete(dto.participationId);
                this.loadPage();
            },
        });
    }

    hasGradedPresentationChanged(dto: ParticipationManagementDTO): boolean {
        return this.participationsChangedPresentation.has(dto.participationId);
    }

    changeGradedPresentation(dto: ParticipationManagementDTO) {
        this.participationsChangedPresentation.set(dto.participationId, dto);
    }

    removePresentation(dto: ParticipationManagementDTO) {
        if (!this.basicPresentationEnabled() && !this.gradedPresentationEnabled()) return;
        const p = this.toStudentParticipation(dto);
        p.presentationScore = undefined;
        this.participationService.update(this.exercise()!, p).subscribe({
            next: () => this.loadPage(),
            error: () => this.alertService.error('artemisApp.participation.removePresentation.error'),
        });
    }

    isEditingDueDate(id: number): boolean {
        return this.editingDueDateIds().has(id);
    }

    getPendingDueDate(id: number): dayjs.Dayjs | undefined {
        return this.pendingDueDates.get(id);
    }

    setPendingDueDate(id: number, value: dayjs.Dayjs | undefined) {
        this.pendingDueDates.set(id, value);
    }

    startEditDueDate(dto: ParticipationManagementDTO) {
        this.pendingDueDates.set(dto.participationId, dto.individualDueDate);
        this.editingDueDateIds.update((s) => new Set([...s, dto.participationId]));
    }

    cancelEditDueDate(dto: ParticipationManagementDTO) {
        this.pendingDueDates.delete(dto.participationId);
        this.editingDueDateIds.update((s) => {
            const next = new Set(s);
            next.delete(dto.participationId);
            return next;
        });
    }

    saveIndividualDueDate(dto: ParticipationManagementDTO) {
        const previousDueDate = dto.individualDueDate;
        const newDueDate = this.pendingDueDates.get(dto.participationId);
        const participation = this.toStudentParticipation(dto);
        participation.individualDueDate = newDueDate;
        this.isSaving.set(true);
        this.participationService.updateIndividualDueDates(this.exercise()!, [participation]).subscribe({
            next: () => {
                dto.individualDueDate = newDueDate;
                this.pendingDueDates.delete(dto.participationId);
                this.editingDueDateIds.update((s) => {
                    const next = new Set(s);
                    next.delete(dto.participationId);
                    return next;
                });
                this.isSaving.set(false);
                this.alertService.success('artemisApp.participation.updateDueDates.success', { name: dto.participantName ?? dto.participantIdentifier });
                this.loadPage();
            },
            error: () => {
                dto.individualDueDate = previousDueDate;
                this.pendingDueDates.delete(dto.participationId);
                this.alertService.error('artemisApp.participation.updateDueDates.error');
                this.isSaving.set(false);
            },
        });
    }

    /**
     * Deletes participation
     * @param participationId the id of the participation that we want to delete
     */
    deleteParticipation(participationId: number) {
        this.participationService.delete(participationId, { deleteBuildPlan: true, deleteRepository: true }).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.pendingDueDates.delete(participationId);
                this.editingDueDateIds.update((s) => {
                    const next = new Set(s);
                    next.delete(participationId);
                    return next;
                });
                this.participationsChangedPresentation.delete(participationId);
                this.loadPage();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Cleans programming exercise participation
     * @param programmingExerciseParticipation the id of the participation that we want to delete
     */
    cleanupProgrammingExerciseParticipation(dto: ParticipationManagementDTO) {
        this.participationService.cleanupBuildPlan(this.toStudentParticipation(dto)).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadPage();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
