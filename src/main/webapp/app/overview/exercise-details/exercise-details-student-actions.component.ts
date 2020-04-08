import { Component, HostBinding, Input, OnInit } from '@angular/core';
import * as moment from 'moment';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { Router } from '@angular/router';
import { AlertService } from 'app/core/alert/alert.service';
import { HttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { isStartExerciseAvailable, participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { User } from 'app/core/user/user.model';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
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

    private user: User;
    profileInfo: ProfileInfo;

    constructor(
        private jhiAlertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private accountService: AccountService,
        private sourceTreeService: SourceTreeService,
        private router: Router,
        private profileService: ProfileService,
    ) {}

    ngOnInit(): void {
        this.accountService.identity().then((user) => {
            this.user = user!;

            // Only load password if current user login starts with 'edx_' or 'u4i_'
            if (user && user.login && (user.login.startsWith('edx_') || user.login.startsWith('u4i_'))) {
                this.getRepositoryPassword();
            }
        });
        // In order to check if the application is currently running on production
        this.profileService.getProfileInfo().subscribe(
            (profileInfo) => {
                if (profileInfo) {
                    this.profileInfo = profileInfo;
                }
            },
            (reason) => {},
        );
    }

    /**
     * The button should be only visible in the dev environment
     */
    visibilityCheck(): boolean {
        return !this.profileInfo.inProduction;
    }

    repositoryUrl(participation: Participation) {
        const programmingParticipation = participation as ProgrammingExerciseStudentParticipation;
        if (programmingParticipation.team) {
            return this.repositoryUrlForTeam(programmingParticipation);
        }
        return programmingParticipation.repositoryUrl;
    }

    /**
     * The user info part of the repository url of a team participation has to be be added with the current user's login.
     *
     * @return repository url with username of current user inserted
     */
    private repositoryUrlForTeam(participation: ProgrammingExerciseStudentParticipation) {
        // (https://)(bitbucket.ase.in.tum.de/...-team1.git)  =>  (https://)ga12abc@(bitbucket.ase.in.tum.de/...-team1.git)
        return participation.repositoryUrl.replace(/^(\w*:\/\/)(.*)$/, `$1${this.user.login}@$2`);
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

    onCopyFailure() {
        console.log('copy fail!');
    }

    onCopySuccess() {
        this.wasCopied = true;
        setTimeout(() => {
            this.wasCopied = false;
        }, 3000);
    }

    /**
     * triggers the simulation of a participation and submission for the currently logged in user
     */
    simulateSubmission() {
        this.courseExerciseService.simulateSubmission(this.exercise.id).subscribe(() => {
            this.jhiAlertService.success('artemisApp.exercise.submissionSuccessful');
        });
    }

    /**
     * triggers the simulation of a result for the currently logged in user
     */
    simulateResult() {
        this.courseExerciseService.simulateResult(this.exercise.id).subscribe(() => {
            this.jhiAlertService.success('artemisApp.exercise.resultCreationSuccessful');
        });
    }

    startExercise() {
        if (this.exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/courses', this.courseId, 'quiz-exercises', this.exercise.id, 'live']);
        }

        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                (participation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('artemisApp.exercise.personalRepository');
                    }
                },
                (error) => {
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
                (participation: StudentParticipation) => {
                    if (participation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        participation.results = this.exercise.studentParticipations[0] ? this.exercise.studentParticipations[0].results : [];
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                },
                (error) => {
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

    /**
     * Returns the id of the team that the student is assigned to (only applicable to team-based exercises)
     *
     * @return {assignedTeamId}
     */
    get assignedTeamId(): number | undefined {
        const participation = this.exercise.studentParticipations[0];
        return participation ? participation.team?.id : this.exercise.studentAssignedTeamId;
    }

    startPractice() {
        return this.router.navigate(['/courses', this.exercise.course?.id, 'quiz-exercises', this.exercise.id, 'practice']);
    }

    getRepositoryPassword() {
        this.sourceTreeService.getRepositoryPassword().subscribe((res) => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }
}
