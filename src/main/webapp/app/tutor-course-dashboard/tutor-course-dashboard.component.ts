import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { AccountService, User } from '../core';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise';
import { SubmissionExerciseType } from 'app/entities/submission';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
import * as moment from 'moment';

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-course-dashboard.component.html',
    providers: [JhiAlertService, CourseService]
})
export class TutorCourseDashboardComponent implements OnInit {
    course: Course;
    courseId: number;
    unfinishedExercises: Exercise[] = [];
    finishedExercises: Exercise[] = [];
    exercises: Exercise[] = [];
    numberOfSubmissions = 0;
    numberOfAssessments = 0;
    numberOfTutorAssessments = 0;
    numberOfComplaints = 0;
    numberOfTutorComplaints = 0;
    showFinishedExercises = false;

    private subscription: Subscription;
    private tutor: User;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute
    ) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = +params.courseId;
            this.loadAll();
        });

        this.route.queryParams.subscribe(queryParams => {
            if (queryParams['welcome'] === '') {
                this.showWelcomeAlert();
            }
        });

        this.accountService.identity().then(user => (this.tutor = user));
    }

    loadAll() {
        this.courseService.getForTutors(this.courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body;

                if (this.course.exercises && this.course.exercises.length > 0) {
                    this.unfinishedExercises = this.course.exercises.filter(exercise => exercise.tutorParticipations[0].status !== TutorParticipationStatus.COMPLETED).sort(this.sortByAssessmentDueDate);
                    this.finishedExercises = this.course.exercises.filter(exercise => exercise.tutorParticipations[0].status === TutorParticipationStatus.COMPLETED).sort(this.sortByAssessmentDueDate);
                    this.exercises = this.unfinishedExercises;

                    for (const exercise of this.course.exercises) {
                        this.numberOfSubmissions += exercise.participations.filter(
                            participation =>
                                participation.submissions.filter(
                                    submission => submission.submissionExerciseType === SubmissionExerciseType.TEXT
                                ).length > 0
                        ).length;
                        this.numberOfAssessments += exercise.participations.filter(
                            participation => participation.results.length > 0
                        ).length;
                        this.numberOfTutorAssessments += exercise.participations.filter(
                            participation => participation.results.filter(result => result.assessor.id === this.tutor.id).length > 0
                        ).length;
                        this.numberOfComplaints += exercise.participations.filter(
                            participation => participation.results.filter(result => result.hasComplaint === true).length > 0
                        ).length;
                    }
                }
            },
            (response: string) => this.onError(response)
        );
    }

    trackId(index: number, item: Course) {
        return item.id;
    }

    triggerFinishedExercises() {
        this.showFinishedExercises = !this.showFinishedExercises;

        if (this.showFinishedExercises) {
            this.exercises = this.unfinishedExercises.concat(this.finishedExercises);
        } else {
            this.exercises = this.unfinishedExercises;
        }
    }

    private sortByAssessmentDueDate(firstEl: Exercise, secondEl: Exercise): number {
        if (!firstEl.assessmentDueDate || !secondEl.assessmentDueDate) {
            return firstEl.id - secondEl.id;
        }

        return moment(firstEl.assessmentDueDate).diff(secondEl.assessmentDueDate);
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }

    showWelcomeAlert() {
        // show alert after timeout to fix translation not loaded
        setTimeout(() => {
            this.jhiAlertService.info('arTeMiSApp.exercise.welcome');
        }, 500);
    }
}
