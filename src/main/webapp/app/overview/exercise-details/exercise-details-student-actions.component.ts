import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Exercise, ExerciseType, isStartExerciseAvailable, ParticipationStatus, participationStatus } from 'app/entities/exercise';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { InitializationState, Participation, ProgrammingExerciseStudentParticipation } from 'app/entities/participation';
import * as moment from 'moment';
import { CourseExerciseService } from 'app/entities/course';
import { Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { HttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { FeatureToggle } from 'app/feature-toggle';
import { Result } from 'app/entities/result';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [JhiAlertService, SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent implements OnInit {
    FeatureToggle = FeatureToggle;
    readonly ExerciseType = ExerciseType;
    readonly ParticipationStatus = ParticipationStatus;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;

    @Input() actionsOnly: boolean;
    @Input() smallButtons: boolean;
    @Input() showResult: boolean;

    public repositoryPassword: string;
    public wasCopied = false;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private accountService: AccountService,
        private sourceTreeService: SourceTreeService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.accountService.identity().then(user => {
            // Only load password if current user login starts with 'edx_' or 'u4i_'
            if (user && user.login && (user.login.startsWith('edx_') || user.login.startsWith('u4i_'))) {
                this.getRepositoryPassword();
            }
        });
    }

    repositoryUrl(participation: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
    }

    isPracticeModeAvailable(): boolean {
        const quizExercise = this.exercise as QuizExercise;
        return quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(quizExercise.dueDate!).isBefore(moment());
    }

    /**
     * see exercise-utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    isOnlineEditorAllowed(): boolean {
        return (this.exercise as ProgrammingExercise).allowOnlineEditor;
    }

    publishBuildPlanUrl(): boolean {
        return (this.exercise as ProgrammingExercise).publishBuildPlanUrl;
    }

    buildPlanActive(): boolean {
        return (
            this.exercise.studentParticipations &&
            this.exercise.studentParticipations.length > 0 &&
            this.exercise.studentParticipations[0].initializationState !== InitializationState.INACTIVE
        );
    }

    goToBuildPlan(participation: Participation) {
        // TODO: get the continuous integration URL as a client constant during the management info call
        window.open('https://bamboobruegge.in.tum.de/browse/' + (participation as ProgrammingExerciseStudentParticipation).buildPlanId);
    }

    onCopyFailure() {
        console.log('copy fail!');
    }

    onCopySuccess() {
        this.wasCopied = true;
        setTimeout(() => {
            this.wasCopied = false;
        }, 3000);
    }

    startExercise() {
        if (this.exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/quiz', this.exercise.id]);
        }

        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('artemisApp.exercise.personalRepository');
                    }
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return this.sourceTreeService.buildSourceTreeUrl(cloneUrl);
    }

    resumeProgrammingExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .resumeProgrammingExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        participation.results = this.exercise.studentParticipations[0] ? this.exercise.studentParticipations[0].results : [];
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                },
                error => {
                    console.log('Error: ' + error.status + ' ' + error.message);
                    this.jhiAlertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            );
    }

    /**
     * Wrapper for using participationStatus() in the template
     *
     * @return {ParticipationStatus}
     */
    participationStatusWrapper(): ParticipationStatus {
        return participationStatus(this.exercise);
    }

    startPractice() {
        return this.router.navigate(['/quiz', this.exercise.id, 'practice']);
    }

    getRepositoryPassword() {
        this.sourceTreeService.getRepositoryPassword().subscribe(res => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }
}
