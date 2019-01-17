import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { Principal, User } from '../core';
import { HttpResponse } from '@angular/common/http';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';

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

    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;

    private subscription: Subscription;
    private tutor: User;
    private tutorParticipation: TutorParticipation;

    constructor(
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private principal: Principal,
        private route: ActivatedRoute,
        private tutorParticipationService: TutorParticipationService,
        private textSubmissionService: TextSubmissionService,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = +params.courseId;
            this.exerciseId = +params.exerciseId;
            this.loadAll();
        });

        this.route.queryParams.subscribe(queryParams => {
            if (queryParams['welcome'] === '') {
                this.showWelcomeAlert();
            }
        });

        this.principal.identity()
            .then(user => this.tutor = user);
    }

    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body;
                this.tutorParticipation = this.exercise.tutorParticipations[0];
                this.tutorParticipationStatus = this.tutorParticipation.status;
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
        this.textSubmissionService.
            getTextSubmissionForExerciseWithoutAssessment(this.exerciseId)
            .subscribe((response: HttpResponse<TextSubmission>) => {
                this.unassessedSubmission = response.body;
            });
    }

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    readInstruction() {
        this.tutorParticipationService.create(this.tutorParticipation, this.exerciseId).subscribe(
            (res: HttpResponse<TutorParticipation>) => {
                this.tutorParticipation = res.body;
            },
            error => console.error(error)
        );
    }

    trackId(index: number, item: Course) {
        return item.id;
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    showWelcomeAlert() {
        // show alert after timeout to fix translation not loaded
        setTimeout(() => {
            this.jhiAlertService.info('arTeMiSApp.exercise.welcome');
        }, 500);
    }
}
