import { Component, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { faClockRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/programming/shared/actions/student-repo-download/programming-exercise-student-repo-download.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
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
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private router = inject(Router);

    readonly codeEditorContainer = viewChild(CodeEditorContainerComponent);

    PROGRAMMING = ExerciseType.PROGRAMMING;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    readonly getCourseFromExercise = getCourseFromExercise;

    paramSub: Subscription;
    // These fields are set inside async callbacks (route.params subscribe, HTTP subscribe/tap, identity().then)
    // and read in the template, so they must be signals to render under zoneless change detection.
    readonly participation = signal<ProgrammingExerciseStudentParticipation>(undefined!);
    readonly exercise = signal<ProgrammingExercise>(undefined!);
    userId: number;
    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    readonly loadingParticipation = signal(false);
    readonly participationCouldNotBeFetched = signal(false);
    showEditorInstructions = true;
    readonly routeCommitHistory = signal<string>(undefined!);
    readonly vcsAccessLogRoute = signal<string>(undefined!);
    readonly repositoryUri = signal<string>(undefined!);
    readonly repositoryType = signal<RepositoryType>(undefined!);
    readonly auxiliaryRepositoryId = signal<number | undefined>(undefined);
    readonly enableVcsAccessLog = signal(false);
    readonly allowVcsAccessLog = signal(false);
    readonly result = signal<Result>(undefined!);
    readonly resultHasInlineFeedback = signal(false);
    readonly showInlineFeedback = signal(false);

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
        this.routeCommitHistory.set(this.router.url + '/commit-history');
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation.set(true);
            this.participationCouldNotBeFetched.set(false);
            this.resetRepositoryRouteState();
            const exerciseId = Number(params['exerciseId']);
            const repositoryId = Number(params['repositoryId']);
            const repositoryType = params['repositoryType'] ?? RepositoryType.USER;
            this.repositoryType.set(repositoryType);
            this.vcsAccessLogRoute.set(this.router.url + '/vcs-access-log');
            this.enableVcsAccessLog.set(this.router.url.includes('course-management') && params['repositoryType'] !== RepositoryType.TESTS);
            if (repositoryType === RepositoryType.USER) {
                this.loadStudentParticipation(repositoryId);
            } else if (repositoryType === RepositoryType.AUXILIARY) {
                this.auxiliaryRepositoryId.set(repositoryId);
                this.loadAuxiliaryRepository(exerciseId, repositoryId);
            } else {
                this.loadDifferentParticipation(repositoryType, exerciseId);
            }
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
                    const exercise = exerciseResponse.body!;
                    this.exercise.set(exercise);
                    if (repositoryType === RepositoryType.TEMPLATE) {
                        this.participation.set(exercise.templateParticipation!);
                        this.domainService.setDomain([DomainType.PARTICIPATION, exercise.templateParticipation!]);
                        this.repositoryUri.set(exercise.templateParticipation!.repositoryUri!);
                    } else if (repositoryType === RepositoryType.SOLUTION) {
                        this.participation.set(exercise.solutionParticipation!);
                        this.domainService.setDomain([DomainType.PARTICIPATION, exercise.solutionParticipation!]);
                        this.repositoryUri.set(exercise.solutionParticipation!.repositoryUri!);
                    } else if (repositoryType === RepositoryType.TESTS) {
                        this.domainService.setDomain([DomainType.TEST_REPOSITORY, exercise]);
                        this.repositoryUri.set(exercise.testRepositoryUri!);
                    } else {
                        this.participationCouldNotBeFetched.set(true);
                        this.loadingParticipation.set(false);
                    }
                    this.allowVcsAccessLog.set(this.accountService.isAtLeastInstructorInCourse(this.getCourseFromExercise(exercise)));
                }),
            )
            .subscribe({
                next: () => {
                    this.loadingParticipation.set(false);
                },
                error: () => {
                    this.participationCouldNotBeFetched.set(true);
                    this.loadingParticipation.set(false);
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
                    this.participation.set(participationWithResults);
                    const exercise = participationWithResults.exercise as ProgrammingExercise;
                    this.exercise.set(exercise);
                    this.allowVcsAccessLog.set(this.accountService.isAtLeastInstructorInCourse(this.getCourseFromExercise(exercise)));
                    this.repositoryUri.set(participationWithResults.repositoryUri!);
                }),
            )
            .subscribe({
                next: () => {
                    this.loadingParticipation.set(false);
                },
                error: () => {
                    this.participationCouldNotBeFetched.set(true);
                    this.loadingParticipation.set(false);
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
                const results = participation.submissions?.last()?.results;
                if (results && results.length) {
                    // connect result, submission, and participation — the result component derives the
                    // participation (and through it the exercise) from this back-reference when no
                    // participation input is provided
                    results[0].submission = participation.submissions?.last();
                    results[0].submission!.participation = participation;
                    this.result.set(results[0]);
                    this.resultHasInlineFeedback.set(results[0].feedbacks?.some((feedback) => Feedback.getReferenceLine(feedback) !== undefined) ?? false);
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
                    const exercise = exerciseResponse.body!;
                    this.exercise.set(exercise);
                    const auxiliaryRepo = exercise.auxiliaryRepositories?.find((repo) => repo.id === auxiliaryRepositoryId);
                    if (auxiliaryRepo) {
                        this.domainService.setDomain([DomainType.AUXILIARY_REPOSITORY, auxiliaryRepo]);
                        this.repositoryUri.set(auxiliaryRepo.repositoryUri!);
                    }
                }),
            )
            .subscribe({
                next: () => {
                    this.loadingParticipation.set(false);
                },
                error: () => {
                    this.participationCouldNotBeFetched.set(true);
                },
            });
    }

    private resetRepositoryRouteState() {
        this.participation.set(undefined!);
        this.repositoryUri.set(undefined!);
        this.auxiliaryRepositoryId.set(undefined);
        this.result.set(undefined!);
        this.resultHasInlineFeedback.set(false);
        this.showInlineFeedback.set(false);
    }
}
