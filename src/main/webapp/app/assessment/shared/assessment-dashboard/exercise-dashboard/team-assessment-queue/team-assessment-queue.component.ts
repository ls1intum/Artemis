import { Component, computed, inject, input, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCodeCommit, faEllipsisVertical, faFolderOpen, faUsers } from '@fortawesome/free-solid-svg-icons';
import {
    CellTemplateRef,
    ColumnDef,
    TumAetUiButtonDirective,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiTableComponent,
    TumAetUiTableQueryEvent,
    TumAetUiTagComponent,
    TumAetUiTagSeverity,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { finalize } from 'rxjs/operators';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { CorrectionRoundResultDTO, ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import { managementDtoToParticipation, managementDtoToResult } from 'app/exercise/participation/participation-management.util';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type TeamAssessmentState = 'noSubmission' | 'notAssessed' | 'inProgress' | 'complaint' | 'assessed';

const STATE_SEVERITY: Record<TeamAssessmentState, TumAetUiTagSeverity> = {
    noSubmission: 'secondary',
    notAssessed: 'info',
    inProgress: 'warn',
    complaint: 'danger',
    assessed: 'success',
};

/**
 * Derives where the assessment of a team stands from its newest manual result, which belongs to the latest correction round.
 */
export function getTeamAssessmentState(dto: ParticipationManagementDTO): TeamAssessmentState {
    if (!dto.submissionId) {
        return 'noSubmission';
    }
    const manualResult = (dto.correctionRoundResults ?? []).reduce<CorrectionRoundResultDTO | undefined>(
        (latest, result) => (!latest || (result.correctionRound ?? 0) > (latest.correctionRound ?? 0) ? result : latest),
        undefined,
    );
    if (!manualResult) {
        return 'notAssessed';
    }
    if (manualResult.hasComplaint) {
        return 'complaint';
    }
    return manualResult.completionDate ? 'assessed' : 'inProgress';
}

/**
 * The teams the current tutor owns in a team exercise, with their assessment state and the actions to assess them.
 * Only the team tutor may assess a team, so this is the tutor's assessment queue for the exercise.
 */
@Component({
    selector: 'jhi-team-assessment-queue',
    templateUrl: './team-assessment-queue.component.html',
    imports: [
        RouterLink,
        FaIconComponent,
        TumAetUiTableComponent,
        TumAetUiTagComponent,
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        TumAetUiTooltipDirective,
        ManageAssessmentButtonsComponent,
        ResultComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class TeamAssessmentQueueComponent {
    private readonly participationService = inject(ParticipationService);
    private readonly alertService = inject(AlertService);

    readonly exercise = input.required<Exercise>();

    readonly rows = signal<ParticipationManagementDTO[]>([]);
    readonly totalRecords = signal(0);
    readonly isLoading = signal(false);
    private lastQuery?: TumAetUiTableQueryEvent;

    readonly course = computed(() => getCourseFromExercise(this.exercise())!);
    readonly isProgrammingExercise = computed(() => this.exercise().type === ExerciseType.PROGRAMMING);

    readonly teamTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('teamTemplate');
    readonly statusTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('statusTemplate');
    readonly resultTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('resultTemplate');

    readonly columns = computed<ColumnDef<ParticipationManagementDTO>[]>(() => [
        { field: 'participantName', headerKey: 'artemisApp.exerciseAssessmentDashboard.myTeams.team', sort: true, templateRef: this.teamTemplate() },
        { headerKey: 'artemisApp.exerciseAssessmentDashboard.myTeams.status', templateRef: this.statusTemplate() },
        { field: 'score', headerKey: 'artemisApp.exerciseAssessmentDashboard.myTeams.result', sort: true, templateRef: this.resultTemplate() },
    ]);

    protected readonly faEllipsisVertical = faEllipsisVertical;
    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faCodeCommit = faCodeCommit;
    protected readonly faUsers = faUsers;

    loadPage(query: TumAetUiTableQueryEvent) {
        this.lastQuery = query;
        this.isLoading.set(true);
        this.participationService
            .searchParticipations(this.exercise().id!, {
                page: query.pageIndex,
                pageSize: query.pageSize,
                sortingOrder: query.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
                sortedColumn: query.sort?.field ?? 'participantName',
                searchTerm: query.searchTerm ?? '',
                ownTeams: true,
            })
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: (page) => {
                    this.rows.set(page.content);
                    this.totalRecords.set(page.totalElements);
                },
                error: (error) => onError(this.alertService, error),
            });
    }

    /** Reloads the current page, e.g. after an assessment was cancelled. */
    refresh() {
        if (this.lastQuery) {
            this.loadPage(this.lastQuery);
        }
    }

    state(dto: ParticipationManagementDTO): TeamAssessmentState {
        return getTeamAssessmentState(dto);
    }

    stateSeverity(dto: ParticipationManagementDTO): TumAetUiTagSeverity {
        return STATE_SEVERITY[this.state(dto)];
    }

    teamMembers(dto: ParticipationManagementDTO): string {
        return (dto.teamStudents ?? []).map((student) => student.name ?? student.login).join(', ');
    }

    toParticipation(dto: ParticipationManagementDTO) {
        return managementDtoToParticipation(dto, this.exercise());
    }

    toResult(dto: ParticipationManagementDTO) {
        return managementDtoToResult(dto);
    }

    repositoryLink(dto: ParticipationManagementDTO): (string | number)[] {
        return ['/course-management', this.course().id!, 'programming-exercises', this.exercise().id!, 'repository', RepositoryType.USER, dto.participationId];
    }

    commitHistoryLink(dto: ParticipationManagementDTO): (string | number)[] {
        return [...this.repositoryLink(dto), 'commit-history'];
    }

    teamLink(dto: ParticipationManagementDTO): (string | number)[] {
        return ['/course-management', this.course().id!, 'exercises', this.exercise().id!, 'teams', dto.teamId!];
    }
}
