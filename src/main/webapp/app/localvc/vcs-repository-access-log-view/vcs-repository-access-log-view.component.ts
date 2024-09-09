import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    standalone: true,
})
export class VcsRepositoryAccessLogViewComponent implements OnInit, OnDestroy {
    participationId: number;
    vcsAccessLogEntries: VcsAccessLogDTO[];
    protected dialogErrorSource = new Subject<string>();

    paramSub: Subscription;
    routeVcsAccessLog: string;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;

    constructor(
        public domainService: DomainService,
        private route: ActivatedRoute,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private router: Router,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.routeVcsAccessLog = this.router.url + '/vcs-access-log';
        this.paramSub = this.route.params.subscribe((params) => {
            const participationId = Number(params['participationId']);
            const exerciseId = Number(params['exerciseId']);
            const repositoryType = params['repositoryType'];
            if (participationId) {
                this.loadVcsAccessLogForParticipation(participationId);
            } else {
                this.loadVcsAccessLog(exerciseId, repositoryType);
            }
        });
    }

    ngOnDestroy(): void {
        this.paramSub.unsubscribe();
    }

    public loadVcsAccessLogForParticipation(participationId: number) {
        const accessLogEntries: Observable<VcsAccessLogDTO[] | undefined> = this.programmingExerciseParticipationService.getVcsAccessLogForParticipation(participationId);
        this.extractEntries(accessLogEntries);
    }

    public loadVcsAccessLog(exerciseId: number, repositoryType: string) {
        const accessLogEntries: Observable<VcsAccessLogDTO[] | undefined> = this.programmingExerciseParticipationService.getVcsAccessLogForRepository(exerciseId, repositoryType);
        this.extractEntries(accessLogEntries);
    }

    private extractEntries(accessLogEntries: Observable<VcsAccessLogDTO[] | undefined>) {
        accessLogEntries.subscribe({
            next: (next: VcsAccessLogDTO[] | undefined) => {
                if (next) {
                    this.vcsAccessLogEntries = next;
                    this.dialogErrorSource.next('');
                }
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.repository.vcsAccessLog.error');
            },
        });
    }
}
