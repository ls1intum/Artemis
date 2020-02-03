import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, of, Subject } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { Result } from 'app/entities/result';
import { JhiAlertService } from 'ng-jhipster';
import { ParticipationType } from './programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { FeatureToggle } from 'app/feature-toggle';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html',
    styleUrls: ['./programming-exercise-detail.component.scss'],
})
export class ProgrammingExerciseDetailComponent implements OnInit, OnDestroy {
    readonly ActionType = ActionType;
    readonly ParticipationType = ParticipationType;
    readonly FeatureToggle = FeatureToggle;
    readonly JAVA = ProgrammingLanguage.JAVA;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    programmingExercise: ProgrammingExercise;

    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;
    gradingInstructions: SafeHtml | null;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activatedRoute: ActivatedRoute,
        private accountService: AccountService,
        private programmingExerciseService: ProgrammingExerciseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private artemisMarkdown: ArtemisMarkdown,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.gradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.programmingExercise.gradingInstructions);
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

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
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

    combineTemplateCommits() {
        this.programmingExerciseService.combineTemplateRepositoryCommits(this.programmingExercise.id).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.programmingExercise.combineTemplateCommitsSuccess');
            },
            () => {
                this.jhiAlertService.error('artemisApp.programmingExercise.combineTemplateCommitsError');
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
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
