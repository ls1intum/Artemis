import { Component, computed, effect, inject, signal } from '@angular/core';
import { Observable, lastValueFrom } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { AlertService } from 'app/core/util/alert.service';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    standalone: true,
})
export class VcsRepositoryAccessLogViewComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly alertService = inject(AlertService);

    private readonly vcsAccessLogEntries = signal<VcsAccessLogDTO[]>([]);

    private readonly params = toSignal(this.route.params, { requireSync: true });
    private readonly participationId = computed(() => {
        const participationId = this.params().participationId;
        if (participationId) {
            return Number(participationId);
        }
        return undefined;
    });
    private readonly exerciseId = computed(() => Number(this.params().exerciseId));
    private readonly repositoryType = computed(() => String(this.params().repositoryType));

    constructor() {
        effect(
            async () => {
                if (this.participationId()) {
                    await this.loadVcsAccessLogForParticipation(this.participationId()!);
                } else {
                    await this.loadVcsAccessLog(this.exerciseId(), this.repositoryType());
                }
            },
            { allowSignalWrites: true },
        );
    }

    public async loadVcsAccessLogForParticipation(participationId: number) {
        await this.extractEntries(() => this.programmingExerciseParticipationService.getVcsAccessLogForParticipation(participationId));
    }

    public async loadVcsAccessLog(exerciseId: number, repositoryType: string) {
        await this.extractEntries(() => this.programmingExerciseParticipationService.getVcsAccessLogForRepository(exerciseId, repositoryType));
    }

    private async extractEntries(fun: () => Observable<VcsAccessLogDTO[] | undefined>) {
        try {
            const accessLogEntries = await lastValueFrom(fun());
            if (accessLogEntries) {
                this.vcsAccessLogEntries.set(accessLogEntries);
            }
        } catch (error) {
            this.alertService.error('artemisApp.repository.vcsAccessLog.error');
        }
    }
}
