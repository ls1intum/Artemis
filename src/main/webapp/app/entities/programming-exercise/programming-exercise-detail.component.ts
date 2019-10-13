import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { Result, ResultService } from 'app/entities/result';
import { JhiAlertService } from 'ng-jhipster';
import { ParticipationType } from './programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { AccountService } from 'app/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';

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

    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;

    constructor(
        private activatedRoute: ActivatedRoute,
        private accountService: AccountService,
        private programmingExerciseService: ProgrammingExerciseService,
        private resultService: ResultService,
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

            this.programmingExerciseParticipationService
                .getLatestResultWithFeedback(this.programmingExercise.solutionParticipation.id)
                .pipe(catchError(() => of(null)))
                .subscribe((result: Result) => {
                    this.programmingExercise.solutionParticipation.results = result ? [result] : [];
                    this.loadingSolutionParticipationResults = false;
                });

            this.programmingExerciseParticipationService
                .getLatestResultWithFeedback(this.programmingExercise.templateParticipation.id)
                .pipe(catchError(() => of(null)))
                .subscribe((result: Result) => {
                    this.programmingExercise.templateParticipation.results = result ? [result] : [];
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

    /**
     * Cleans up programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     * @param $event passed from delete dialog to represent if checkboxes were checked
     */
    cleanupProgrammingExercise(programmingExerciseId: number, $event: { [key: string]: boolean }) {
        this.exerciseService.cleanup(programmingExerciseId, $event.deleteRepositories).subscribe(
            () => {
                if ($event.deleteRepositories) {
                    this.jhiAlertService.success('Cleanup was successful. All build plans and repositories have been deleted. All participations have been marked as Finished.');
                } else {
                    this.jhiAlertService.success('Cleanup was successful. All build plans have been deleted. Students can resume their participation.');
                }
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }
}
