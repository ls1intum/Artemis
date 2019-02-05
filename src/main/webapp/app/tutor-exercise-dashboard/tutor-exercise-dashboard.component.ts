import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { AccountService, User } from '../core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ExampleSubmission } from 'app/entities/example-submission';

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-exercise-dashboard.component.html',
    providers: [JhiAlertService, CourseService]
})
export class TutorExerciseDashboardComponent implements OnInit {
    exercise: Exercise;
    courseId: number;
    exerciseId: number;
    numberOfTutorAssessments = 0;
    tutorParticipationStatus: TutorParticipationStatus;
    submissions: TextSubmission[];
    unassessedSubmission: TextSubmission;
    exampleSubmissionsToReview: ExampleSubmission[] = [];
    exampleSubmissionsToAssess: ExampleSubmission[] = [];
    exampleSubmissionsCompletedByTutor: ExampleSubmission[] = [];
    tutorParticipation: TutorParticipation;
    nextExampleSubmissionId: number;

    stats = {
        toReview: {
            done: 0,
            total: 0
        },
        toAssess: {
            done: 0,
            total: 0
        }
    };

    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    private subscription: Subscription;
    private tutor: User;

    constructor(
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private tutorParticipationService: TutorParticipationService,
        private textSubmissionService: TextSubmissionService,
        private modalService: NgbModal,
        private router: Router
    ) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = +params.courseId;
            this.exerciseId = +params.exerciseId;
            this.loadAll();
        });

        this.accountService.identity().then(user => (this.tutor = user));
    }

    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body;
                this.tutorParticipation = this.exercise.tutorParticipations[0];
                this.tutorParticipationStatus = this.tutorParticipation.status;
                this.exampleSubmissionsToReview = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => exampleSubmission.usedForTutorial);
                this.exampleSubmissionsToAssess = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => !exampleSubmission.usedForTutorial);
                this.exampleSubmissionsCompletedByTutor = this.tutorParticipation.trainedExampleSubmissions;

                this.stats.toReview.total = this.exampleSubmissionsToReview.length;
                this.stats.toReview.done = this.exampleSubmissionsCompletedByTutor.filter(e => e.usedForTutorial).length;
                this.stats.toAssess.total = this.exampleSubmissionsToAssess.length;
                this.stats.toAssess.done = this.exampleSubmissionsCompletedByTutor.filter(e => !e.usedForTutorial).length;

                if (this.stats.toReview.done < this.stats.toReview.total) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToReview[this.stats.toReview.done].id;
                } else if (this.stats.toAssess.done < this.stats.toAssess.total) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToAssess[this.stats.toAssess.done].id;
                }

            },
            (response: string) => this.onError(response)
        );

        this.getSubmissions();
        this.getSubmissionWithoutAssessment();
    }

    private getSubmissions(): void {
        this.textSubmissionService
            .getTextSubmissionsForExerciseAssessedByTutor(this.exerciseId)
            .map((response: HttpResponse<TextSubmission[]>) =>
                response.body.map((submission: TextSubmission) => {
                    if (submission.result) {
                        // reconnect some associations
                        submission.result.submission = submission;
                        submission.result.participation = submission.participation;
                        submission.participation.results = [submission.result];
                    }

                    return submission;
                })
            )
            .subscribe((submissions: TextSubmission[]) => {
                this.submissions = submissions;
                this.numberOfTutorAssessments = submissions.filter(submission => submission.result.completionDate).length;
            });
    }

    private getSubmissionWithoutAssessment(): void {
        this.textSubmissionService
            .getTextSubmissionForExerciseWithoutAssessment(this.exerciseId)
            .subscribe((response: HttpResponse<TextSubmission>) => {
                this.unassessedSubmission = response.body;
            }, (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there aren't unassessed submission, nothing we have to worry about
                } else {
                    this.onError(error.message);
                }
            });
    }

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    readInstruction(onComplete?: () => void) {
        this.tutorParticipationService.create(this.tutorParticipation, this.exerciseId).subscribe(
            (res: HttpResponse<TutorParticipation>) => {
                this.tutorParticipation = res.body;
                this.tutorParticipationStatus = this.tutorParticipation.status;
                this.jhiAlertService.success('arTeMiSApp.tutorExerciseDashboard.participation.instructionsReviewed');
            },
            this.onError,
            () => {
                if (onComplete) {
                    onComplete();
                }
            }
        );
    }

    hasBeenCompletedByTutor(id: number) {
        return this.exampleSubmissionsCompletedByTutor.filter(e => e.id === id).length > 0;
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    calculateStatus(submission: TextSubmission) {
        if (submission.result.completionDate) {
            return 'DONE';
        }

        return 'DRAFT';
    }
}
