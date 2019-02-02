import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Course, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { AccountService, User } from '../core';
import { HttpResponse } from '@angular/common/http';
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
    exampleSubmissionsForTutorial: ExampleSubmission[] = [];
    exampleSubmissionsToComplete: ExampleSubmission[] = [];
    exampleSubmissionsCompletedByTutor: ExampleSubmission[] = [];
    tutorParticipation: TutorParticipation;
    nextExampleSubmissionId: number;

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
        private location: Location
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
                this.exampleSubmissionsForTutorial = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => exampleSubmission.usedForTutorial);
                this.exampleSubmissionsToComplete = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => !exampleSubmission.usedForTutorial);
                this.exampleSubmissionsCompletedByTutor = this.tutorParticipation.trainedExampleSubmissions;

                console.log(this.exampleSubmissionsForTutorial)
                console.log(this.exampleSubmissionsCompletedByTutor)

                if (this.exampleSubmissionsCompletedByTutor.length < this.exampleSubmissionsForTutorial.length) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsForTutorial[this.exampleSubmissionsCompletedByTutor.length].id;
                } else {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToComplete[this.exampleSubmissionsCompletedByTutor.length - this.exampleSubmissionsForTutorial.length].id;
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
                this.numberOfTutorAssessments = submissions.length;
            });
    }

    private getSubmissionWithoutAssessment(): void {
        this.textSubmissionService
            .getTextSubmissionForExerciseWithoutAssessment(this.exerciseId)
            .subscribe((response: HttpResponse<TextSubmission>) => {
                this.unassessedSubmission = response.body;
            });
    }

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    readInstruction(onComplete: () => void) {
        this.tutorParticipationService.create(this.tutorParticipation, this.exerciseId).subscribe(
            (res: HttpResponse<TutorParticipation>) => {
                this.tutorParticipation = res.body;
                this.tutorParticipationStatus = this.tutorParticipation.status;
                this.jhiAlertService.success('You reviewed the instructions, you can now read the example submissions');
            },
            this.onError,
            onComplete
        );
    }

    hasBeenCompletedByTutor(id: number) {
        return this.exampleSubmissionsCompletedByTutor.filter(e => e.id === id).length > 0;
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }

    back() {
        this.location.back();
    }
}
