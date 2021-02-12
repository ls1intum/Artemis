import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Submission, SubmissionType } from 'app/entities/submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import * as moment from 'moment';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    readonly ParticipationType = ParticipationType;
    @Input() participationId: number;
    public exerciseStatusBadge = 'badge-success';

    participation: Participation;
    exercise: Exercise;
    submissions: Submission[];
    eventSubscriber: Subscription;
    isLoading = true;

    constructor(
        private route: ActivatedRoute,
        private submissionService: SubmissionService,
        private participationService: ParticipationService,
        private exerciseService: ExerciseService,
        private programmingExerciseService: ProgrammingExerciseService,
        private eventManager: JhiEventManager,
        private translate: TranslateService,
    ) {}

    /**
     * Initialize component by setting up page and subscribe to eventManager
     */
    ngOnInit() {
        this.setupPage();
        this.eventSubscriber = this.eventManager.subscribe('submissionsModification', () => this.setupPage());
    }

    /**
     * Set up page by loading participation and all submissions
     */
    setupPage() {
        this.isLoading = true;
        this.route.params.subscribe((params) => {
            this.participationId = +params['participationId'];
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
                this.exerciseStatusBadge = moment(this.exercise.dueDate!).isBefore(moment()) ? 'badge-danger' : 'badge-success';
                if (this.exercise.type === ExerciseType.PROGRAMMING) {
                    this.programmingExerciseService.findWithTemplateAndSolutionParticipation(this.exercise.id!, true).subscribe((response) => {
                        const programmingExercise = response.body!;
                        const templateParticipation = programmingExercise.templateParticipation;
                        const solutionParticipation = programmingExercise.solutionParticipation;
                        if (templateParticipation?.id === this.participationId) {
                            this.participation = templateParticipation;
                            this.submissions = templateParticipation.submissions!;
                        } else if (solutionParticipation?.id === this.participationId) {
                            this.participation = solutionParticipation;
                            this.submissions = solutionParticipation.submissions!;
                        } else {
                            this.fetchParticipationAndSubmissionsForStudent();
                        }
                    });
                } else {
                    this.fetchParticipationAndSubmissionsForStudent();
                }
            });
        });
    }

    fetchParticipationAndSubmissionsForStudent() {
        this.participationService
            .find(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of(null)),
            )
            .subscribe((participation) => {
                if (participation) {
                    this.participation = participation;
                    this.isLoading = false;
                }
            });

        this.submissionService
            .findAllSubmissionsOfParticipation(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of([])),
            )
            .subscribe((submissions) => {
                if (submissions) {
                    this.submissions = submissions;
                    console.log('this.submissions', this.submissions);
                    this.isLoading = false;
                }
            });
    }

    getName() {
        if (this.participation.type === ParticipationType.STUDENT) {
            return (this.participation as StudentParticipation).student?.name || (this.participation as StudentParticipation).team?.name;
        } else if (this.participation.type === ParticipationType.SOLUTION) {
            return this.translate.instant('artemisApp.participation.solutionParticipation');
        } else if (this.participation.type === ParticipationType.TEMPLATE) {
            return this.translate.instant('artemisApp.participation.templateParticipation');
        }
        return 'N/A';
    }

    isTemplateOrSolutionParticipation(): boolean {
        return this.participation.type === ParticipationType.TEMPLATE || this.participation.type === ParticipationType.SOLUTION;
    }

    getCommitUrl(submission: ProgrammingSubmission): string | undefined {
        let repoUrl: string | undefined;
        if (submission.type === SubmissionType.TEST) {
            repoUrl = (this.exercise as ProgrammingExercise).testRepositoryUrl;
        } else if (this.participation.type === ParticipationType.PROGRAMMING) {
            repoUrl = (this.participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
        } else if (this.participation.type === ParticipationType.SOLUTION) {
            repoUrl = (this.participation as SolutionProgrammingExerciseParticipation).repositoryUrl;
        } else if (this.participation.type === ParticipationType.TEMPLATE) {
            repoUrl = (this.participation as TemplateProgrammingExerciseParticipation).repositoryUrl;
        }
        if (repoUrl) {
            let baseUrl = repoUrl.replace('.git', '');
            // TODO: find a better way to distinguish between Bitbucket and GitLab urls
            if (repoUrl.includes('/scm/')) {
                // Bitbucket Repository
                const position = baseUrl.lastIndexOf('/');
                baseUrl = [baseUrl.slice(0, position), '/repos', baseUrl.slice(position)].join('');
                repoUrl = baseUrl + '/commits/' + submission.commitHash;
                repoUrl = repoUrl.replace('scm', 'projects');
            } else {
                // GitLab Repository
                repoUrl = baseUrl + '/-/commit/' + submission.commitHash;
            }
            return repoUrl;
        }
        return undefined;
    }
}
