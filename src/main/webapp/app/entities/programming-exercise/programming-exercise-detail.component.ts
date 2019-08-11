import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { filter, map, tap, catchError } from 'rxjs/operators';

import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { Result, ResultService } from 'app/entities/result';
import { JhiAlertService } from 'ng-jhipster';
import { ParticipationType } from './programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ExerciseType } from 'app/entities/exercise';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html',
    styleUrls: ['./programming-exercise-detail.component.scss'],
})
export class ProgrammingExerciseDetailComponent implements OnInit {
    ParticipationType = ParticipationType;
    readonly JAVA = ProgrammingLanguage.JAVA;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    programmingExercise: ProgrammingExercise;

    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;

    constructor(
        private activatedRoute: ActivatedRoute,
        private programmingExerciseService: ProgrammingExerciseService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;

            this.programmingExercise.solutionParticipation.programmingExercise = this.programmingExercise;
            this.programmingExercise.templateParticipation.programmingExercise = this.programmingExercise;

            this.loadLatestResultWithFeedback(this.programmingExercise.solutionParticipation.id).subscribe((results: Result[]) => {
                this.programmingExercise.solutionParticipation.results = results;
                this.loadingSolutionParticipationResults = false;
            });

            this.loadLatestResultWithFeedback(this.programmingExercise.templateParticipation.id).subscribe((results: Result[]) => {
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
    private loadLatestResultWithFeedback(participationId: number) {
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
            error => {
                const errorMessage = error.headers.get('X-artemisApp-alert');
                // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
                const jhiAlert = this.jhiAlertService.error(errorMessage);
                jhiAlert.msg = errorMessage;
            },
        );
    }
}
