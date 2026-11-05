import { LanguageTableCellComponent } from '../language-table-cell/language-table-cell.component';
import { SortService } from 'app/foundation/service/sort.service';
import { Language } from 'app/course/shared/entities/course.model';
import { Component, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
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
import { map, tap } from 'rxjs/operators';
import { Observable, of } from 'rxjs';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { CorrectionRoundResultDTO } from 'app/exercise/participation/participation-management-dto.model';
import { AssessmentQueueRowDTO } from 'app/exercise/participation/assessment-queue-dto.model';
import { scoreDtoToParticipation } from 'app/exercise/exercise-scores/participation-score.util';
import { PageableResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
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
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

type AssessmentState = 'noSubmission' | 'notAssessed' | 'inProgress' | 'complaint' | 'assessed';

interface AssessmentQueueRow {
    dto: AssessmentQueueRowDTO;
    state: AssessmentState;
    severity: TumAetUiTagSeverity;
    lockedByAnotherTutor: boolean;
    teamMembers: string;
    resultsForRound: CorrectionRoundResultDTO[];
    languageSubmission: TextSubmission;
    participation: ReturnType<typeof scoreDtoToParticipation>;
    result: Result | undefined;
    ownedCorrectionRounds: number[];
    repositoryLink: (string | number)[];
    commitHistoryLink: (string | number)[];
    teamLink: (string | number)[];
}

const STATE_SEVERITY: Record<AssessmentState, TumAetUiTagSeverity> = {
    noSubmission: 'secondary',
    notAssessed: 'info',
    inProgress: 'warn',
    complaint: 'danger',
    assessed: 'success',
};

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
        this.participationRows().some((dto) =>
            dto.assessableCorrectionRounds?.some(
                (round) => round < this.correctionRoundsEnabled() && !dto.correctionRoundResults?.some((result) => (result.correctionRound ?? 0) === round),
            ),
        ),
    );

    private readonly query = signal<TumAetUiTableQueryEvent | undefined>(undefined);
    private readonly pageResource = rxResource({
        params: () => {
            const query = this.query();
            return query
                ? { query, exercise: this.exercise(), correctionRound: this.correctionRound(), isTestRun: this.isTestRun(), assessmentDisabled: this.assessmentDisabled() }
                : undefined;
        },
        stream: ({ params }) => this.createPageRequest(params.query).pipe(tap({ error: (error) => onError(this.alertService, error) })),
    });
    private readonly participationRows = computed(() => (this.pageResource.hasValue() ? this.pageResource.value().content : []));
    readonly rows = computed<AssessmentQueueRow[]>(() => this.participationRows().map((dto) => this.prepareRow(dto)));
    readonly totalRecords = linkedSignal<number | undefined, number>({
        source: () => (this.pageResource.hasValue() ? this.pageResource.value().totalElements : undefined),
        computation: (total, previous) => total ?? previous?.value ?? 0,
    });
    readonly isLoading = this.pageResource.isLoading;

    readonly course = computed(() => getCourseFromExercise(this.exercise())!);
    readonly isProgrammingExercise = computed(() => this.exercise().type === ExerciseType.PROGRAMMING);

    readonly participantTemplate = viewChild<CellTemplateRef<AssessmentQueueRow>>('participantTemplate');
    readonly statusTemplate = viewChild<CellTemplateRef<AssessmentQueueRow>>('statusTemplate');
    readonly submissionDateTemplate = viewChild<CellTemplateRef<AssessmentQueueRow>>('submissionDateTemplate');
    readonly languageTemplate = viewChild<CellTemplateRef<AssessmentQueueRow>>('languageTemplate');
    readonly resultTemplate = viewChild<CellTemplateRef<AssessmentQueueRow>>('resultTemplate');

    readonly columns = computed<ColumnDef<AssessmentQueueRow>[]>(() => [
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

    loadPage(query: TumAetUiTableQueryEvent) {
        this.query.set(query);
    }

    private createPageRequest(query: TumAetUiTableQueryEvent): Observable<PageableResult<AssessmentQueueRowDTO>> {
        if (this.isTestRun()) {
            return this.submissionService.getTestRunSubmissionsForExercise(this.exercise().id!).pipe(map((response) => this.submissionPage(response.body ?? [], query)));
        } else if (this.examMode()) {
            const round = this.correctionRound();
            if (round === undefined) {
                return of({ content: [], totalElements: 0 });
            }
            return this.submissionService
                .getExamAssessmentQueue(this.exercise().type!, this.exercise().id!, round, !this.assessmentDisabled(), !!this.exercise().isAtLeastInstructor)
                .pipe(map((submissions) => this.submissionPage(submissions, query)));
        } else {
            return this.participationService.searchAssessmentQueue(this.exercise().id!, {
                page: query.pageIndex,
                pageSize: query.pageSize,
                sortingOrder: query.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
                sortedColumn: query.sort?.field ?? this.participantSortField(),
                searchTerm: this.teamMode() ? (query.searchTerm ?? '') : '',
            });
        }
    }

    private submissionPage(submissions: Submission[], query: TumAetUiTableQueryEvent) {
        const round = this.correctionRound();
        const content = submissions.map((submission): AssessmentQueueRowDTO => {
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
                olderAssessmentDrafts: [],
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

    private prepareRow(dto: AssessmentQueueRowDTO): AssessmentQueueRow {
        const state = this.getAssessmentState(dto);
        const participation = scoreDtoToParticipation(dto, this.exercise());
        const repositoryLink = this.repositoryLink(dto);
        return {
            dto,
            state,
            severity: STATE_SEVERITY[state],
            lockedByAnotherTutor: this.lockedByAnotherTutor(dto),
            teamMembers: this.teamMembers(dto),
            resultsForRound: this.resultsForRound(dto),
            languageSubmission: this.languageSubmission(dto),
            participation,
            result: dto.resultId ? participation.submissions?.[0]?.results?.find((result) => result.id === dto.resultId) : undefined,
            ownedCorrectionRounds: this.ownedCorrectionRounds(dto),
            repositoryLink,
            commitHistoryLink: [...repositoryLink, 'commit-history'],
            teamLink: this.teamLink(dto),
        };
    }

    /** Reloads the current page, e.g. after an assessment was cancelled. */
    refresh() {
        this.pageResource.reload();
    }

    /**
     * Derives where an assessment stands from its newest manual result, which belongs to the latest correction round.
     */
    private getAssessmentState(dto: AssessmentQueueRowDTO): AssessmentState {
        const correctionRound = this.correctionRound();
        if (!dto.submissionId) {
            return 'noSubmission';
        }
        const manualResult = (dto.correctionRoundResults ?? [])
            .filter((result) => correctionRound === undefined || result.correctionRound === correctionRound)
            .reduce<CorrectionRoundResultDTO | undefined>(
                (latest, result) => (!latest || (result.correctionRound ?? 0) > (latest.correctionRound ?? 0) ? result : latest),
                undefined,
            );
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

    resultsForRound(dto: AssessmentQueueRowDTO): CorrectionRoundResultDTO[] {
        return (dto.correctionRoundResults ?? []).filter((result) => result.correctionRound === this.correctionRound());
    }

    lockedByAnotherTutor(dto: AssessmentQueueRowDTO): boolean {
        return !this.isTestRun() && !this.exercise().isAtLeastInstructor && !!dto.correctionRoundResults?.some((result) => !result.completionDate && !result.ownedByCurrentUser);
    }

    languageSubmission(dto: AssessmentQueueRowDTO): TextSubmission {
        const submission = new TextSubmission();
        submission.language = dto.submissionLanguage as Language;
        return submission;
    }

    teamMembers(dto: AssessmentQueueRowDTO): string {
        return (dto.teamStudents ?? []).map((student) => student.name ?? student.login).join(', ');
    }

    ownedCorrectionRounds(dto: AssessmentQueueRowDTO): number[] {
        return (dto.correctionRoundResults ?? []).filter((result) => result.ownedByCurrentUser).map((result) => result.correctionRound ?? 0);
    }

    repositoryLink(dto: AssessmentQueueRowDTO): (string | number)[] {
        return getLocalRepositoryLink(
            this.course().id!,
            this.exercise().id!,
            RepositoryType.USER,
            dto.participationId,
            this.exercise().exerciseGroup?.id,
            this.exercise().exerciseGroup?.exam?.id,
        );
    }

    teamLink(dto: AssessmentQueueRowDTO): (string | number)[] {
        return ['/course-management', this.course().id!, 'exercises', this.exercise().id!, 'teams', dto.teamId!];
    }
}
