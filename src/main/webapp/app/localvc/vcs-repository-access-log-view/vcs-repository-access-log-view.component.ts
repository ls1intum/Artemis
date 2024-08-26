import { Component, OnInit } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
})
export class VcsRepositoryAccessLogViewComponent implements OnInit {
    participationId: number;
    vcsAccessLogEntries: VcsAccessLogDTO[];
    protected dialogErrorSource = new Subject<string>();

    paramSub: Subscription;
    routeVcsAccessLog: string;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;

    constructor(
        private accountService: AccountService,
        public domainService: DomainService,
        private route: ActivatedRoute,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private programmingExerciseService: ProgrammingExerciseService,
        private router: Router,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.routeVcsAccessLog = this.router.url + '/vcs-access-log';
        this.paramSub = this.route.params.subscribe((params) => {
            const participationId = Number(params['participationId']);
            const exerciseId = Number(params['exerciseId']);
            const reposiotryType = params['repositoryType'];
            if (participationId) {
                this.loadVcsAccessLogForParticipation(participationId);
            } else {
                this.loadVcsAccessLog(exerciseId, reposiotryType);
            }
        });
    }

    private loadVcsAccessLogForParticipation(participationId: number) {
        this.programmingExerciseParticipationService.getVcsAccessLogForParticipation(participationId).subscribe({
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

    private loadVcsAccessLog(exerciseId: number, reposiotryType: any) {
        this.programmingExerciseParticipationService.getVcsAccessLogForExerciseRepository(exerciseId, reposiotryType).subscribe({
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
