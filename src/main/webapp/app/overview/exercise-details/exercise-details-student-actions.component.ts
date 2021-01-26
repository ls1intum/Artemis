import { Component, HostBinding, Input, OnInit } from '@angular/core';
import * as moment from 'moment';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
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
import { TranslateService } from '@ngx-translate/core';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LocalStorageService } from 'ngx-webstorage';

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

    @Input() examMode: boolean;

    public repositoryPassword: string;
    public wasCopied = false;
    public useSsh = false;

    public sshEnabled = false;
    private sshTemplateUrl: string;
    public sshKeysUrl: string;
    private baseUrl: string;

    private user: User;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private accountService: AccountService,
        private sourceTreeService: SourceTreeService,
        private router: Router,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private localStorage: LocalStorageService,
    ) {}

    /**
     * load password if user login starts with 'edx_' or 'u4i_'
     */
    ngOnInit(): void {
        this.accountService.identity().then((user) => {
            this.user = user!;

            // Only load password if current user login starts with 'edx_' or 'u4i_'
            if (user && user.login && (user.login.startsWith('edx_') || user.login.startsWith('u4i_'))) {
                this.getRepositoryPassword();
            }
        });
        this.profileService.getProfileInfo().subscribe((info: ProfileInfo) => {
            this.sshKeysUrl = info.sshKeysURL;
            this.sshTemplateUrl = info.sshCloneURLTemplate;
            this.sshEnabled = !!this.sshTemplateUrl;
            if (info.versionControlUrl) {
                this.baseUrl = info.versionControlUrl;
            }
        });

        this.useSsh = this.localStorage.retrieve('useSsh') || false;
        this.localStorage.observe('useSsh').subscribe((useSsh) => (this.useSsh = useSsh || false));
    }

    public toggleUseSsh(): void {
        this.useSsh = !this.useSsh;
        this.localStorage.store('useSsh', this.useSsh);
    }

    /**
     * get repositoryUrl for participation
     *
     * @param {Participation} participation
     */
    repositoryUrl(participation?: Participation) {
        const programmingParticipation = participation as ProgrammingExerciseStudentParticipation;
        if (this.useSsh) {
            // the same ssh url is used for individual and team exercises
            return this.getSshCloneUrl(programmingParticipation?.repositoryUrl);
        }
        if (programmingParticipation?.team) {
            return this.repositoryUrlForTeam(programmingParticipation);
        }
        return programmingParticipation?.repositoryUrl;
    }

    /**
     * The user info part of the repository url of a team participation has to be be added with the current user's login.
     *
     * @return repository url with username of current user inserted
     */
    private repositoryUrlForTeam(participation?: ProgrammingExerciseStudentParticipation) {
        // (https://)(bitbucket.ase.in.tum.de/...-team1.git)  =>  (https://)ga12abc@(bitbucket.ase.in.tum.de/...-team1.git)
        return participation?.repositoryUrl?.replace(/^(\w*:\/\/)(.*)$/, `$1${this.user.login}@$2`);
    }

    /**
     * check if practiceMode is available
     * @return {boolean}
     */
    isPracticeModeAvailable(): boolean {
        const quizExercise = this.exercise as QuizExercise;
        return quizExercise.isPlannedToStart! && quizExercise.isOpenForPractice! && moment(quizExercise.dueDate!).isBefore(moment());
    }

    /**
     * see exercise-utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     * check if onlineEditor is allowed
     * @return {boolean}
     */
    isOnlineEditorAllowed() {
        return (this.exercise as ProgrammingExercise).allowOnlineEditor;
    }

    /**
     * check if offline IDE is allowed
     * @return {boolean}
     */
    isOfflineIdeAllowed() {
        return (this.exercise as ProgrammingExercise).allowOfflineIde;
    }

    /**
     * console log if copy fails
     */
    onCopyFailure() {}

    /**
     * set wasCopied for 3 seconds on success
     */
    onCopySuccess() {
        this.wasCopied = true;
        setTimeout(() => {
            this.wasCopied = false;
        }, 3000);
    }

    /**
     * start the exercise
     */
    startExercise() {
        if (this.exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/courses', this.courseId, 'quiz-exercises', this.exercise.id, 'live']);
        }

        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id!)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                (participation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        if ((this.exercise as ProgrammingExercise).allowOfflineIde) {
                            this.jhiAlertService.success('artemisApp.exercise.personalRepositoryClone');
                        } else {
                            this.jhiAlertService.success('artemisApp.exercise.personalRepositoryOnline');
                        }
                    }
                },
                () => {
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    /**
     * build the sourceTreeUrl from the cloneUrl
     * @param {string} cloneUrl
     * @return sourceTreeUrl
     */
    buildSourceTreeUrl(cloneUrl?: string) {
        return this.sourceTreeService.buildSourceTreeUrl(this.baseUrl, cloneUrl);
    }

    /**
     * resume the programming exercise
     */
    resumeProgrammingExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .resumeProgrammingExercise(this.courseId, this.exercise.id!)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                (participation: StudentParticipation) => {
                    if (participation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        participation.results = this.exercise.studentParticipations![0] ? this.exercise.studentParticipations![0].results : [];
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                },
                (error) => {
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
        const participation = this.exercise.studentParticipations![0];
        return participation ? participation.team?.id : this.exercise.studentAssignedTeamId;
    }

    /**
     * start practice
     */
    startPractice() {
        return this.router.navigate(['/courses', this.exercise.course?.id, 'quiz-exercises', this.exercise.id, 'practice']);
    }

    /**
     * get the repositoryPassword
     */
    getRepositoryPassword() {
        this.sourceTreeService.getRepositoryPassword().subscribe((res) => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }

    /**
     * Transforms the repository url to a ssh url
     */
    getSshCloneUrl(url?: string) {
        return url?.replace(/^\w*:\/\/[^/]*?\/(scm\/)?(.*)$/, this.sshTemplateUrl + '$2');
    }

    /**
     * Inserts the correct link to the translated ssh tip.
     */
    getSshKeyTip() {
        return this.translateService.instant('artemisApp.exerciseActions.sshKeyTip').replace(/{link:(.*)}/, '<a href="' + this.sshKeysUrl + '" target="_blank">$1</a>');
    }
}
