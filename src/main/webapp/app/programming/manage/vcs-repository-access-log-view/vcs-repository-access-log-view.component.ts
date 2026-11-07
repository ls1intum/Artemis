import { Component, computed, inject, viewChild } from '@angular/core';
import { of } from 'rxjs';
import { catchError, map, startWith, switchMap } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { VcsAccessLogDTO } from 'app/programming/shared/entities/vcs-access-log-entry.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

interface VcsAccessLogRow {
    id?: number;
    userId?: number;
    author: string;
    repositoryActionType: string;
    authenticationMechanism: string;
    commitHash?: string;
    timestamp: string;
}

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    imports: [TranslateDirective, CourseTitleBarTitleDirective, TableViewComponent, ArtemisDatePipe],
    host: { class: 'flex h-full min-h-0 min-w-0 flex-col' },
})
export class VcsRepositoryAccessLogViewComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly alertService = inject(AlertService);

    private readonly timestampTemplate = viewChild<CellTemplateRef<VcsAccessLogRow>>('timestampTemplate');
    private readonly commitHashTemplate = viewChild<CellTemplateRef<VcsAccessLogRow>>('commitHashTemplate');
    private readonly params = toSignal(this.route.params, { requireSync: true });
    private readonly exerciseId = computed(() => Number(this.params().exerciseId));
    private readonly repositoryType = computed(() => String(this.params().repositoryType) as RepositoryType);
    private readonly participationId = computed(() => {
        if (this.repositoryType() !== RepositoryType.USER) {
            return undefined;
        }
        const id = this.params().repositoryId;
        return id ? Number(id) : undefined;
    });
    private readonly fetchParams = computed(() => ({
        participationId: this.participationId(),
        exerciseId: this.exerciseId(),
        repositoryType: this.repositoryType(),
    }));

    private readonly logState = toSignal(
        toObservable(this.fetchParams).pipe(
            switchMap((params) =>
                (params.participationId
                    ? this.programmingExerciseParticipationService.getVcsAccessLogForParticipation(params.participationId)
                    : this.programmingExerciseParticipationService.getVcsAccessLogForRepository(params.exerciseId, params.repositoryType)
                ).pipe(
                    map((entries) => ({ entries: entries ?? [], loading: false })),
                    catchError(() => {
                        this.alertService.error('artemisApp.repository.vcsAccessLog.error');
                        return of({ entries: [], loading: false });
                    }),
                    startWith({ entries: [], loading: true }),
                ),
            ),
        ),
        { initialValue: { entries: [] as VcsAccessLogDTO[], loading: true } },
    );

    protected readonly loading = computed(() => this.logState().loading);
    protected readonly rows = computed<VcsAccessLogRow[]>(() =>
        this.logState().entries.map((entry) => ({
            id: entry.id,
            userId: entry.userId,
            author: [entry.name, entry.email].filter(Boolean).join(', '),
            repositoryActionType: entry.repositoryActionType,
            authenticationMechanism: entry.authenticationMechanism,
            commitHash: entry.commitHash,
            // Search dates in the viewer's timezone, matching the displayed timestamp.
            timestamp: dayjs(entry.timestamp).local().format('YYYY-MM-DDTHH:mm:ss.SSSZ'),
        })),
    );

    protected readonly tableOptions: TableViewOptions = {
        lazy: false,
        scrollable: true,
        scrollHeight: 'flex',
        searchPlaceholder: 'artemisApp.repository.vcsAccessLog.searchPlaceholder',
        emptyMessageTranslation: 'artemisApp.repository.vcsAccessLog.empty',
        initialSortField: 'timestamp',
        initialSortOrder: -1,
    };

    protected readonly columns = computed<ColumnDef<VcsAccessLogRow>[]>(() => [
        { field: 'userId', headerKey: 'artemisApp.repository.vcsAccessLog.userId', sort: true, width: '7rem' },
        { field: 'author', headerKey: 'artemisApp.repository.vcsAccessLog.author', sort: true },
        { field: 'repositoryActionType', headerKey: 'artemisApp.repository.vcsAccessLog.actionType', sort: true, width: '9rem' },
        { field: 'authenticationMechanism', headerKey: 'artemisApp.repository.vcsAccessLog.authMechanism', sort: true, width: '12rem' },
        { field: 'commitHash', headerKey: 'artemisApp.repository.vcsAccessLog.commitHash', templateRef: this.commitHashTemplate() },
        {
            field: 'timestamp',
            headerKey: 'artemisApp.repository.vcsAccessLog.timeStamp',
            sort: true,
            sortComparator: (first, second) => dayjs(first.timestamp).valueOf() - dayjs(second.timestamp).valueOf(),
            templateRef: this.timestampTemplate(),
            width: '14rem',
        },
    ]);
}
