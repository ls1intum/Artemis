import { LanguageTableCellComponent } from '../language-table-cell/language-table-cell.component';
import { SortService } from 'app/foundation/service/sort.service';
import { Language } from 'app/course/shared/entities/course.model';
import { Component, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
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
import { CorrectionRoundResultDTO, ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import { scoreDtoToParticipation, scoreDtoToResult } from 'app/exercise/exercise-scores/participation-score.util';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { Submission, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { getLocalRepositoryLink } from 'app/foundation/util/navigation.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
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
export function getAssessmentState(dto: ParticipationManagementDTO, correctionRound?: number): AssessmentState {
    if (!dto.submissionId) {
        return 'noSubmission';
    }
    const manualResult = (dto.correctionRoundResults ?? [])
        .filter((result) => correctionRound === undefined || result.correctionRound === correctionRound)
        .reduce<CorrectionRoundResultDTO | undefined>((latest, result) => (!latest || (result.correctionRound ?? 0) > (latest.correctionRound ?? 0) ? result : latest), undefined);
    if (!manualResult) {
        return 'notAssessed';
    }
    if (manualResult.hasComplaint) {
        return 'complaint';
    }
    if (!manualResult.completionDate) {
        return 'inProgress';
    }
    return 'assessed';
}

@Component({
    selector: 'jhi-assessment-queue',
    templateUrl: './assessment-queue.component.html',
    imports: [
        LanguageTableCellComponent,
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
        ArtemisDatePipe,
    ],
})
export class AssessmentQueueComponent {
    private readonly sortService = inject(SortService);
    private readonly participationService = inject(ParticipationService);
    private readonly submissionService = inject(SubmissionService);
    private readonly alertService = inject(AlertService);

    readonly exercise = input.required<Exercise>();
    readonly isTestRun = input(false);
    readonly correctionRound = input<number>();
    readonly assessmentDisabled = input(false);
    readonly assessmentDisabledTooltip = input('');
    readonly correctionRoundsEnabled = input(1);
    readonly assessmentChanged = output<void>();
    readonly teamMode = computed(() => !!this.exercise().teamMode);
    readonly participantSortField = computed(() => (this.teamMode() ? 'participantName' : 'participationId'));
    readonly translationKey = computed(
        () => 'artemisApp.exerciseAssessmentDashboard.' + (this.teamMode() ? (this.exercise().isAtLeastInstructor ? 'allTeams' : 'myTeams') : 'students'),
    );
    readonly examMode = computed(() => !!this.exercise().exerciseGroup);
    readonly hasAssessableSubmissions = computed(() =>
        this.rows().some((row) =>
            row.assessableCorrectionRounds?.some(
                (round) => round < this.correctionRoundsEnabled() && !row.correctionRoundResults?.some((result) => (result.correctionRound ?? 0) === round),
            ),
        ),
    );

    readonly rows = signal<ParticipationManagementDTO[]>([]);
    readonly totalRecords = signal(0);
    readonly isLoading = signal(false);
    private lastQuery?: TumAetUiTableQueryEvent;
    private currentLoadRequestId = 0;

    readonly course = computed(() => getCourseFromExercise(this.exercise())!);
    readonly isProgrammingExercise = computed(() => this.exercise().type === ExerciseType.PROGRAMMING);

    readonly participantTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('participantTemplate');
    readonly statusTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('statusTemplate');
    readonly submissionDateTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('submissionDateTemplate');
    readonly languageTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('languageTemplate');
    readonly resultTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('resultTemplate');

    readonly columns = computed<ColumnDef<ParticipationManagementDTO>[]>(() => [
        { field: this.participantSortField(), headerKey: this.translationKey() + '.participant', sort: true, templateRef: this.participantTemplate() },
        { field: 'submissionDate', headerKey: 'artemisApp.exerciseAssessmentDashboard.submissionDate', sort: true, templateRef: this.submissionDateTemplate() },
        ...(this.exercise().type === ExerciseType.TEXT
            ? [{ field: 'submissionLanguage', headerKey: 'artemisApp.exerciseAssessmentDashboard.language', sort: true, templateRef: this.languageTemplate() }]
            : []),
        { headerKey: 'artemisApp.exerciseAssessmentDashboard.myTeams.status', templateRef: this.statusTemplate() },
        {
            field: 'score',
            headerKey: 'artemisApp.exerciseAssessmentDashboard.myTeams.result',
            sort: true,
            templateRef: this.resultTemplate(),
        },
    ]);

    protected readonly faEllipsisVertical = faEllipsisVertical;
    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faCodeCommit = faCodeCommit;
    protected readonly faUsers = faUsers;

    constructor() {
        let wasDisabled = false;
        effect(() => {
            const disabled = this.assessmentDisabled();
            if (wasDisabled && !disabled) {
                untracked(() => this.refresh());
            }
            wasDisabled = disabled;
        });
    }

    loadPage(query: TumAetUiTableQueryEvent) {
        const requestId = ++this.currentLoadRequestId;
        this.lastQuery = query;
        this.isLoading.set(true);
        const request = this.isTestRun()
            ? this.submissionService.getTestRunSubmissionsForExercise(this.exercise().id!).pipe(map((response) => this.submissionPage(response.body ?? [], query)))
            : this.examMode()
              ? this.submissionService
                    .getExamAssessmentQueue(this.exercise().type!, this.exercise().id!, this.correctionRound()!, !this.assessmentDisabled())
                    .pipe(map((submissions) => this.submissionPage(submissions, query)))
              : this.participationService.searchAssessmentQueue(this.exercise().id!, {
                    page: query.pageIndex,
                    pageSize: query.pageSize,
                    sortingOrder: query.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
                    sortedColumn: query.sort?.field ?? this.participantSortField(),
                    searchTerm: this.teamMode() ? (query.searchTerm ?? '') : '',
                    filterProp: 'NoPracticeMode',
                });
        request
            .pipe(
                finalize(() => {
                    if (requestId === this.currentLoadRequestId) {
                        this.isLoading.set(false);
                    }
                }),
            )
            .subscribe({
                next: (page) => {
                    if (requestId === this.currentLoadRequestId) {
                        this.rows.set(page.content);
                        this.totalRecords.set(page.totalElements);
                    }
                },
                error: (error) => {
                    if (requestId === this.currentLoadRequestId) {
                        onError(this.alertService, error);
                    }
                },
            });
    }

    private submissionPage(submissions: Submission[], query: TumAetUiTableQueryEvent) {
        const round = this.correctionRound();
        const content = submissions.map((submission): ParticipationManagementDTO => {
            const result =
                round === undefined
                    ? getLatestSubmissionResult(submission)
                    : submission.results?.find(
                          (result) =>
                              result.correctionRound === round && result.assessmentType !== AssessmentType.AUTOMATIC && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA,
                      );
            return {
                participationId: submission.participation!.id!,
                submissionCount: 1,
                submissionId: submission.id,
                submissionDate: submission.submissionDate,
                submissionLanguage: (submission as TextSubmission).language,
                testRun: this.isTestRun(),
                resultId: result?.id,
                score: result?.score,
                successful: result?.successful,
                assessmentType: result?.assessmentType,
                completionDate: result?.completionDate,
                assessableCorrectionRounds: round !== undefined && !result && !this.assessmentDisabled() ? [round] : [],
                correctionRoundResults: (submission.results ?? [])
                    .filter((result) => result.id && result.assessmentType !== AssessmentType.AUTOMATIC && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA)
                    .map((result) => ({
                        resultId: result.id!,
                        correctionRound: result.correctionRound,
                        assessmentType: result.assessmentType,
                        completionDate: result.completionDate,
                        hasComplaint: result.hasComplaint,
                        score: result.score,
                        ownedByCurrentUser: result.correctionRound === round,
                    })),
            };
        });
        this.sortService.sortByProperty(content, query.sort?.field ?? 'participationId', query.sort?.direction !== 'desc');
        return { content: content.slice(query.pageIndex * query.pageSize, (query.pageIndex + 1) * query.pageSize), totalElements: content.length };
    }

    /** Reloads the current page, e.g. after an assessment was cancelled. */
    refresh() {
        if (this.lastQuery) {
            this.loadPage(this.lastQuery);
        }
    }

    resultsForRound(dto: ParticipationManagementDTO): CorrectionRoundResultDTO[] {
        return (dto.correctionRoundResults ?? []).filter((result) => result.correctionRound === this.correctionRound());
    }

    state(dto: ParticipationManagementDTO): AssessmentState {
        return getAssessmentState(dto, this.correctionRound());
    }

    stateSeverity(dto: ParticipationManagementDTO): TumAetUiTagSeverity {
        return STATE_SEVERITY[this.state(dto)];
    }

    lockedByAnotherTutor(dto: ParticipationManagementDTO): boolean {
        return !this.isTestRun() && !this.exercise().isAtLeastInstructor && !!dto.correctionRoundResults?.some((result) => !result.completionDate && !result.ownedByCurrentUser);
    }

    languageSubmission(dto: ParticipationManagementDTO): TextSubmission {
        const submission = new TextSubmission();
        submission.language = dto.submissionLanguage as Language;
        return submission;
    }

    teamMembers(dto: ParticipationManagementDTO): string {
        return (dto.teamStudents ?? []).map((student) => student.name ?? student.login).join(', ');
    }

    ownedCorrectionRounds(dto: ParticipationManagementDTO): number[] {
        return (dto.correctionRoundResults ?? []).filter((result) => result.ownedByCurrentUser).map((result) => result.correctionRound ?? 0);
    }

    toParticipation(dto: ParticipationManagementDTO) {
        return scoreDtoToParticipation(dto, this.exercise());
    }

    toResult(dto: ParticipationManagementDTO) {
        return scoreDtoToResult(dto);
    }

    repositoryLink(dto: ParticipationManagementDTO): (string | number)[] {
        return getLocalRepositoryLink(
            this.course().id!,
            this.exercise().id!,
            RepositoryType.USER,
            dto.participationId,
            this.exercise().exerciseGroup?.id,
            this.exercise().exerciseGroup?.exam?.id,
        );
    }

    commitHistoryLink(dto: ParticipationManagementDTO): (string | number)[] {
        return [...this.repositoryLink(dto), 'commit-history'];
    }

    teamLink(dto: ParticipationManagementDTO): (string | number)[] {
        return ['/course-management', this.course().id!, 'exercises', this.exercise().id!, 'teams', dto.teamId!];
    }
}
