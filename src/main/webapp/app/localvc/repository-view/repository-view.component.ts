import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/entities/result.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faClockRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { Feedback } from 'app/entities/feedback.model';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-repository-view',
    templateUrl: './repository-view.component.html',
})
export class RepositoryViewComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;

    PROGRAMMING = ExerciseType.PROGRAMMING;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    readonly getCourseFromExercise = getCourseFromExercise;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    userId: number;
    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    loadingParticipation = false;
    participationCouldNotBeFetched = false;
    showEditorInstructions = true;
    routeCommitHistory: string;
    vcsAccessLogRoute: string;
    repositoryUri: string;
    repositoryType: ProgrammingExerciseInstructorRepositoryType | 'USER';
    enableVcsAccessLog = false;
    allowVcsAccessLog = false;
    result: Result;
    resultHasInlineFeedback = false;
    showInlineFeedback = false;
    localVcEnabled = false;

    faClockRotateLeft = faClockRotateLeft;
    participationWithLatestResultSub: Subscription;
    differentParticipationSub: Subscription;

    constructor(
        private accountService: AccountService,
        public domainService: DomainService,
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private programmingExerciseService: ProgrammingExerciseService,
        private router: Router,
    ) {}

    /**
     * Unsubscribe from all subscriptions when the component is destroyed
     */
    ngOnDestroy() {
        this.paramSub?.unsubscribe();
        this.participationWithLatestResultSub?.unsubscribe();
        this.differentParticipationSub?.unsubscribe();
    }

    /**
     * On init, subscribe to the route params to get the participation and exercise id
     * If the participation id is present, load the participation with the latest result
     * If the participation id is not present, load the template, solution or test participation
     */
    ngOnInit(): void {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.routeCommitHistory = this.router.url + '/commit-history';
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;
            const exerciseId = Number(params['exerciseId']);
            const participationId = Number(params['participationId']);
            this.repositoryType = participationId ? 'USER' : params['repositoryType'];
            this.vcsAccessLogRoute = this.router.url + '/vcs-access-log';
            this.enableVcsAccessLog = this.router.url.includes('course-management') && params['repositoryType'] !== 'TESTS';
            if (this.repositoryType === 'USER') {
                this.loadStudentParticipation(participationId);
            } else {
                this.loadDifferentParticipation(this.repositoryType, exerciseId);
            }
        });
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVcEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });
    }

    /**
     * Load the template, solution or test participation. Set the domain and repositoryUri accordingly.
     * If the participation can't be fetched, set the error state. The test repository does not have a participation.
     * Only the domain is set.
     * @param repositoryType The instructor repository type.
     * @param exerciseId The id of the exercise
     */
    private loadDifferentParticipation(repositoryType: ProgrammingExerciseInstructorRepositoryType, exerciseId: number) {
        this.differentParticipationSub = this.programmingExerciseService
            .findWithTemplateAndSolutionParticipationAndLatestResults(exerciseId)
            .pipe(
                tap((exerciseResponse) => {
                    this.exercise = exerciseResponse.body!;
                    if (repositoryType === 'TEMPLATE') {
                        this.participation = this.exercise.templateParticipation!;
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.templateParticipation!]);
                        this.repositoryUri = this.participation.repositoryUri!;
                    } else if (repositoryType === 'SOLUTION') {
                        this.participation = this.exercise.solutionParticipation!;
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.solutionParticipation!]);
                        this.repositoryUri = this.participation.repositoryUri!;
                    } else if (repositoryType === 'TESTS') {
                        this.domainService.setDomain([DomainType.TEST_REPOSITORY, this.exercise]);
                        this.repositoryUri = this.exercise.testRepositoryUri!;
                    } else {
                        this.participationCouldNotBeFetched = true;
                        this.loadingParticipation = false;
                    }
                    this.allowVcsAccessLog = this.accountService.isAtLeastInstructorInCourse(this.getCourseFromExercise(this.exercise));
                }),
            )
            .subscribe({
                next: () => {
                    this.loadingParticipation = false;
                },
                error: () => {
                    this.participationCouldNotBeFetched = true;
                    this.loadingParticipation = false;
                },
            });
    }

    /**
     * Load the participation with the latest result. Set the domain and repositoryUri accordingly.
     * @param participationId the id of the participation to load
     */
    private loadStudentParticipation(participationId: number) {
        this.participationWithLatestResultSub = this.getParticipationWithLatestResult(participationId)
            .pipe(
                tap((participationWithResults) => {
                    this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                    this.participation = participationWithResults;
                    this.exercise = this.participation.exercise as ProgrammingExercise;
                    this.allowVcsAccessLog = this.accountService.isAtLeastInstructorInCourse(this.getCourseFromExercise(this.exercise));
                    this.repositoryUri = this.participation.repositoryUri!;
                }),
            )
            .subscribe({
                next: () => {
                    this.loadingParticipation = false;
                },
                error: () => {
                    this.participationCouldNotBeFetched = true;
                    this.loadingParticipation = false;
                },
            });
    }

    /**
     * Load the participation from server with the latest result. Set the result and participation accordingly.
     * @param participationId the id of the participation to load
     */
    private getParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            map((participation: ProgrammingExerciseStudentParticipation) => {
                if (participation.results?.length) {
                    // connect result and participation
                    participation.results[0].participation = participation;
                    this.result = participation.results[0];
                    this.resultHasInlineFeedback = this.result.feedbacks?.some((feedback) => Feedback.getReferenceLine(feedback) !== undefined) ?? false;
                }
                return participation;
            }),
        );
    }
}
