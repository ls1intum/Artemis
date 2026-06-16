import { Component, computed, inject } from '@angular/core';
import { EMPTY } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { VcsAccessLogDTO } from 'app/programming/shared/entities/vcs-access-log-entry.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    imports: [TranslateDirective],
})
export class VcsRepositoryAccessLogViewComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly alertService = inject(AlertService);

    private readonly params = toSignal(this.route.params, { requireSync: true });
    private readonly participationId = computed(() => {
        const participationId = this.params().repositoryId;
        if (participationId) {
            return Number(participationId);
        }
        return undefined;
    });
    private readonly exerciseId = computed(() => Number(this.params().exerciseId));
    private readonly repositoryType = computed(() => String(this.params().repositoryType));
    private readonly fetchParams = computed(() => ({
        participationId: this.participationId(),
        exerciseId: this.exerciseId(),
        repositoryType: this.repositoryType(),
    }));

    /**
     * Access log entries for the current route. Replaces the former `effect(async … await …)` data fetch (an effect()
     * misuse): the entries are derived reactively from the route params via a switchMap'd stream — fetching by
     * participation when a participationId is present, otherwise by repository. On error we surface the alert and keep
     * the previously loaded entries (`catchError → EMPTY` makes `toSignal` retain its last value), matching the former
     * try/catch behaviour.
     */
    protected readonly vcsAccessLogEntries = toSignal(
        toObservable(this.fetchParams).pipe(
            switchMap((params) =>
                (params.participationId
                    ? this.programmingExerciseParticipationService.getVcsAccessLogForParticipation(params.participationId)
                    : this.programmingExerciseParticipationService.getVcsAccessLogForRepository(params.exerciseId, params.repositoryType)
                ).pipe(
                    map((entries) => entries ?? []),
                    catchError(() => {
                        this.alertService.error('artemisApp.repository.vcsAccessLog.error');
                        return EMPTY;
                    }),
                ),
            ),
        ),
        { initialValue: [] as VcsAccessLogDTO[] },
    );
}
