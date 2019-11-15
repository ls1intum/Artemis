import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { Result } from 'app/entities/result';
import { JhiAlertService } from 'ng-jhipster';
import { ParticipationType } from './programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { AccountService } from 'app/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { onError } from 'app/utils/global.utils';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html',
    styleUrls: ['./programming-exercise-detail.component.scss'],
})
export class ProgrammingExerciseDetailComponent implements OnInit {
    readonly ActionType = ActionType;
    readonly ParticipationType = ParticipationType;
    readonly JAVA = ProgrammingLanguage.JAVA;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    programmingExercise: ProgrammingExercise;
    closeDialogTrigger: boolean;

    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;

    constructor(
        private activatedRoute: ActivatedRoute,
        private accountService: AccountService,
        private programmingExerciseService: ProgrammingExerciseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.programmingExercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(programmingExercise.course);
            this.programmingExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(programmingExercise.course);

            this.programmingExercise.solutionParticipation.programmingExercise = this.programmingExercise;
            this.programmingExercise.templateParticipation.programmingExercise = this.programmingExercise;

            this.loadLatestResultWithFeedback(this.programmingExercise.solutionParticipation.id).subscribe(results => {
                this.programmingExercise.solutionParticipation.results = results;
                this.loadingSolutionParticipationResults = false;
            });
            this.loadLatestResultWithFeedback(this.programmingExercise.templateParticipation.id).subscribe(results => {
                this.programmingExercise.templateParticipation.results = results;
                this.loadingTemplateParticipationResults = false;
            });
        });
    }

    /**
     * Load the latest result for the given participation. Will return [result] if there is a result, [] if not.
     * @param participationId of the given participation.
     * @return an empty array if there is no result or an array with the single latest result.
     */
    private loadLatestResultWithFeedback(participationId: number): Observable<Result[]> {
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(participationId).pipe(
            catchError(() => of(null)),
            map((result: Result | null) => {
                return result ? [result] : [];
            }),
        );
    }

    previousState() {
        window.history.back();
    }

    squashTemplateCommits() {
        this.programmingExerciseService.squashTemplateRepositoryCommits(this.programmingExercise.id).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.programmingExercise.squashTemplateCommitsSuccess');
            },
            () => {
                this.jhiAlertService.error('artemisApp.programmingExercise.squashTemplateCommitsError');
            },
        );
    }

    generateStructureOracle() {
        this.programmingExerciseService.generateStructureOracle(this.programmingExercise.id).subscribe(
            res => {
                const jhiAlert = this.jhiAlertService.success(res);
                jhiAlert.msg = res;
            },
            error => onError(this.jhiAlertService, error),
        );
    }

    /**
     * Cleans up programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     * @param $event contains additional checks from the dialog
     */
    cleanupProgrammingExercise(programmingExerciseId: number, $event: { [key: string]: boolean }) {
        return this.exerciseService.cleanup(programmingExerciseId, $event.deleteRepositories).subscribe(
            () => {
                if ($event.deleteRepositories) {
                    this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessageWithRepositories');
                } else {
                    this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessage');
                }
                this.closeDialogTrigger = !this.closeDialogTrigger;
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
    }
}
