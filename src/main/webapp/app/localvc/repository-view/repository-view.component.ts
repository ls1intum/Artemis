import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/entities/result.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faClockRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-repository-view',
    templateUrl: './repository-view.component.html',
})
export class RepositoryViewComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;

    PROGRAMMING = ExerciseType.PROGRAMMING;
    protected readonly FeatureToggle = FeatureToggle;

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
    repositoryUri: string;

    result: Result;

    faClockRotateLeft = faClockRotateLeft;
    participationWithLatestResultSub: Subscription;
    differentParticipationSub: Subscription;

    constructor(
        private accountService: AccountService,
        public domainService: DomainService,
        private route: ActivatedRoute,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private programmingExerciseService: ProgrammingExerciseService,
        private router: Router,
    ) {}

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation id with the latest result and result details.
     */
    ngOnInit(): void {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;
            const exerciseId = Number(params['exerciseId']);
            const participationId = Number(params['participationId']);
            if (participationId) {
                this.loadStudentParticipation(participationId);
            } else {
                const repositoryType = params['repositoryType'];
                this.loadDifferentParticipation(repositoryType, exerciseId);
            }
        });
    }

    loadDifferentParticipation(repositoryType: string, exerciseId: number) {
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
                    }
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

    loadStudentParticipation(participationId: number) {
        this.routeCommitHistory = this.router.url + '/commit-history';
        this.participationWithLatestResultSub = this.loadParticipationWithLatestResult(participationId)
            .pipe(
                tap((participationWithResults) => {
                    this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                    this.participation = participationWithResults;
                    this.exercise = this.participation.exercise as ProgrammingExercise;
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
     * Load the participation from server with the latest result.
     * @param participationId
     */
    loadParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            map((participation: ProgrammingExerciseStudentParticipation) => {
                if (participation.results?.length) {
                    // connect result and participation
                    participation.results[0].participation = participation;
                    this.result = participation.results[0];
                }
                return participation;
            }),
        );
    }

    /**
     * If a subscription exists for paramSub, unsubscribe
     */
    ngOnDestroy() {
        this.paramSub?.unsubscribe();
        this.participationWithLatestResultSub?.unsubscribe();
        this.differentParticipationSub?.unsubscribe();
    }
}
