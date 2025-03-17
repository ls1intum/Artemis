import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/entities/result.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faClockRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { Feedback } from 'app/entities/feedback.model';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/programming/shared/actions/programming-exercise-student-repo-download.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/programming-exercise-instructor-repo-download.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { DomainService } from 'app/programming/shared/code-editor/service/code-editor-domain.service';
import { DomainType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-repository-view',
    templateUrl: './repository-view.component.html',
    imports: [
        CodeEditorContainerComponent,
        TranslateDirective,
        ButtonComponent,
        ResultComponent,
        RouterLink,
        FaIconComponent,
        CodeButtonComponent,
        ProgrammingExerciseStudentRepoDownloadComponent,
        ProgrammingExerciseInstructorRepoDownloadComponent,
        ProgrammingExerciseInstructionComponent,
    ],
})
export class RepositoryViewComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    domainService = inject(DomainService);
    private route = inject(ActivatedRoute);
    private profileService = inject(ProfileService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private router = inject(Router);

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
    repositoryType: RepositoryType;
    enableVcsAccessLog = false;
    allowVcsAccessLog = false;
    result: Result;
    resultHasInlineFeedback = false;
    showInlineFeedback = false;
    localVcEnabled = true;

    faClockRotateLeft = faClockRotateLeft;
    participationWithLatestResultSub: Subscription;
    differentParticipationSub: Subscription;

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
            const repositoryId = Number(params['repositoryId']);
            this.repositoryType = params['repositoryType'] ?? RepositoryType.USER;
            this.vcsAccessLogRoute = this.router.url + '/vcs-access-log';
            this.enableVcsAccessLog = this.router.url.includes('course-management') && params['repositoryType'] !== RepositoryType.TESTS;
            if (this.repositoryType === RepositoryType.USER) {
                this.loadStudentParticipation(repositoryId);
            } else if (this.repositoryType === RepositoryType.AUXILIARY) {
                this.loadAuxiliaryRepository(exerciseId, repositoryId);
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
    private loadDifferentParticipation(repositoryType: RepositoryType, exerciseId: number) {
        this.differentParticipationSub = this.programmingExerciseService
            .findWithTemplateAndSolutionParticipationAndLatestResults(exerciseId)
            .pipe(
                tap((exerciseResponse) => {
                    this.exercise = exerciseResponse.body!;
                    if (repositoryType === RepositoryType.TEMPLATE) {
                        this.participation = this.exercise.templateParticipation!;
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.templateParticipation!]);
                        this.repositoryUri = this.participation.repositoryUri!;
                    } else if (repositoryType === RepositoryType.SOLUTION) {
                        this.participation = this.exercise.solutionParticipation!;
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.solutionParticipation!]);
                        this.repositoryUri = this.participation.repositoryUri!;
                    } else if (repositoryType === RepositoryType.TESTS) {
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

    private loadAuxiliaryRepository(exerciseId: number, auxiliaryRepositoryId: number) {
        this.programmingExerciseService
            .findWithAuxiliaryRepository(exerciseId)
            .pipe(
                tap((exerciseResponse) => {
                    this.exercise = exerciseResponse.body!;
                    const auxiliaryRepo = this.exercise.auxiliaryRepositories?.find((repo) => repo.id === auxiliaryRepositoryId);
                    if (auxiliaryRepo) {
                        this.domainService.setDomain([DomainType.AUXILIARY_REPOSITORY, auxiliaryRepo]);
                        this.repositoryUri = auxiliaryRepo.repositoryUri!;
                    }
                }),
            )
            .subscribe({
                next: () => {
                    this.loadingParticipation = false;
                },
                error: () => {
                    this.participationCouldNotBeFetched = true;
                },
            });
    }
}
