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
import { finalize, map } from 'rxjs/operators';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { CorrectionRoundResultDTO, ParticipationScoreDTO } from 'app/exercise/exercise-scores/participation-score-dto.model';
import { scoreDtoToParticipation, scoreDtoToResult } from 'app/exercise/exercise-scores/participation-score.util';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { getLocalRepositoryLink } from 'app/foundation/util/navigation.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type AssessmentState = 'noSubmission' | 'notAssessed' | 'inProgress' | 'complaint' | 'assessed';

const STATE_SEVERITY: Record<AssessmentState, TumAetUiTagSeverity> = {
    noSubmission: 'secondary',
    notAssessed: 'info',
    inProgress: 'warn',
    complaint: 'danger',
    assessed: 'success',
};

/**
 * Derives where an assessment stands from its newest manual result, which belongs to the latest correction round.
 */
export function getAssessmentState(dto: ParticipationScoreDTO): AssessmentState {
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

@Component({
    selector: 'jhi-assessment-queue',
    templateUrl: './assessment-queue.component.html',
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
export class AssessmentQueueComponent {
    private readonly participationService = inject(ParticipationService);
    private readonly submissionService = inject(SubmissionService);
    private readonly alertService = inject(AlertService);

    readonly exercise = input.required<Exercise>();
    readonly isTestRun = input(false);
    readonly assessmentDisabled = input(false);
    readonly assessmentDisabledTooltip = input('');
    readonly correctionRoundsEnabled = input(1);
    readonly teamMode = computed(() => !!this.exercise().teamMode);
    readonly participantSortField = computed(() => (this.teamMode() ? 'participantName' : 'participationId'));
    readonly translationKey = computed(() => 'artemisApp.exerciseAssessmentDashboard.' + (this.teamMode() ? 'myTeams' : 'students'));

    readonly rows = signal<ParticipationScoreDTO[]>([]);
    readonly totalRecords = signal(0);
    readonly isLoading = signal(false);
    private lastQuery?: TumAetUiTableQueryEvent;

    readonly course = computed(() => getCourseFromExercise(this.exercise())!);
    readonly isProgrammingExercise = computed(() => this.exercise().type === ExerciseType.PROGRAMMING);

    readonly participantTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('participantTemplate');
    readonly statusTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('statusTemplate');
    readonly resultTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('resultTemplate');

    readonly columns = computed<ColumnDef<ParticipationScoreDTO>[]>(() => [
        { field: this.participantSortField(), headerKey: this.translationKey() + '.participant', sort: true, templateRef: this.participantTemplate() },
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
        const request = this.isTestRun()
            ? this.submissionService.getTestRunSubmissionsForExercise(this.exercise().id!).pipe(
                  map((response) => {
                      const content = (response.body ?? []).map((submission): ParticipationScoreDTO => {
                          const result = getLatestSubmissionResult(submission);
                          return {
                              participationId: submission.participation!.id!,
                              submissionCount: 1,
                              submissionId: submission.id,
                              testRun: true,
                              resultId: result?.id,
                              score: result?.score,
                              assessmentType: result?.assessmentType,
                              completionDate: result?.completionDate,
                              correctionRoundResults: (submission.results ?? [])
                                  .filter((result) => result.id && result.assessmentType !== AssessmentType.AUTOMATIC && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA)
                                  .map((result) => ({
                                      resultId: result.id!,
                                      correctionRound: result.correctionRound,
                                      assessmentType: result.assessmentType,
                                      completionDate: result.completionDate,
                                      hasComplaint: result.hasComplaint,
                                  })),
                          };
                      });
                      return { content, totalElements: content.length };
                  }),
              )
            : this.participationService.searchAssessmentQueue(this.exercise().id!, {
                  page: query.pageIndex,
                  pageSize: query.pageSize,
                  sortingOrder: query.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
                  sortedColumn: query.sort?.field ?? this.participantSortField(),
                  searchTerm: this.teamMode() ? (query.searchTerm ?? '') : '',
                  ownTeams: this.teamMode(),
                  filterProp: 'NoPracticeMode',
              });
        request.pipe(finalize(() => this.isLoading.set(false))).subscribe({
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

    state(dto: ParticipationScoreDTO): AssessmentState {
        return getAssessmentState(dto);
    }

    stateSeverity(dto: ParticipationScoreDTO): TumAetUiTagSeverity {
        return STATE_SEVERITY[this.state(dto)];
    }

    teamMembers(dto: ParticipationScoreDTO): string {
        return (dto.teamStudents ?? []).map((student) => student.name ?? student.login).join(', ');
    }

    toParticipation(dto: ParticipationScoreDTO) {
        return scoreDtoToParticipation(dto, this.exercise());
    }

    toResult(dto: ParticipationScoreDTO) {
        return scoreDtoToResult(dto);
    }

    repositoryLink(dto: ParticipationScoreDTO): (string | number)[] {
        return getLocalRepositoryLink(
            this.course().id!,
            this.exercise().id!,
            RepositoryType.USER,
            dto.participationId,
            this.exercise().exerciseGroup?.id,
            this.exercise().exerciseGroup?.exam?.id,
        );
    }

    commitHistoryLink(dto: ParticipationScoreDTO): (string | number)[] {
        return [...this.repositoryLink(dto), 'commit-history'];
    }

    teamLink(dto: ParticipationScoreDTO): (string | number)[] {
        return ['/course-management', this.course().id!, 'exercises', this.exercise().id!, 'teams', dto.teamId!];
    }
}
