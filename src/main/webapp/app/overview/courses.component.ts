import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/alert/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import * as moment from 'moment';

@Component({
    selector: 'jhi-overview',
    templateUrl: './courses.component.html',
    styles: [
        `
            .liveQuiz-modal {
                display: block;
                opacity: 1;
            }
        `,
    ],
})
export class CoursesComponent implements OnInit, OnDestroy {
    public courses: Course[];
    public nextRelevantCourse: Course;

    courseForGuidedTour: Course | null;
    quizExercisesChannels: string[];

    isQuizLive = false;
    liveQuiz: QuizExercise;
    liveQuizCourse: Course;

    constructor(
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private jhiAlertService: AlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private guidedTourService: GuidedTourService,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     * First, it loads courses from server {@link loadAndFilterCourses} and subscribes for the team assignment updates {@see teamAssignmentUpdates}
     */
    async ngOnInit() {
        this.loadAndFilterCourses();
        (await this.teamService.teamAssignmentUpdates).subscribe();
    }

    /**
     * Unsubscribe from all websocket subscriptions.
     */
    ngOnDestroy() {
        if (this.quizExercisesChannels) {
            this.quizExercisesChannels.forEach((channel) => this.jhiWebsocketService.unsubscribe(channel));
        }
    }

    /**
     * Loads all courses from server, sets courses in CourseScoreCalculationService {@see setCourses} and
     * enables tour for the course overview for the loaded courses {@see enableTourForCourseOverview}
     */
    loadAndFilterCourses() {
        this.courseService.findAllForDashboard().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseScoreCalculationService.setCourses(this.courses);
                this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseOverviewTour, true);
                // TODO: Stephan Krusche: this is deactivate at the moment, I think we need a more generic solution in more components, e.g. using the the existing notification
                // sent to the client, when a quiz starts. This should slide in from the side.
                // this.subscribeForQuizStartForCourses();
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error('error.unexpectedError', { error }, undefined);
    }

    get nextRelevantExercise(): Exercise | undefined {
        const relevantExercises = new Array<Exercise>();
        let relevantExercise: Exercise | null = null;
        if (this.courses) {
            this.courses.forEach((course) => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
                if (relevantExerciseForCourse) {
                    relevantExerciseForCourse.course = course;
                    relevantExercises.push(relevantExerciseForCourse);
                }
            });
            if (relevantExercises.length === 0) {
                return undefined;
            } else if (relevantExercises.length === 1) {
                relevantExercise = relevantExercises[0];
            } else {
                relevantExercise =
                    // 1st priority is an active quiz
                    relevantExercises.find((exercise: QuizExercise) => exercise.isActiveQuiz) ||
                    // 2nd priority is a visible quiz
                    relevantExercises.find((exercise: QuizExercise) => exercise.isVisibleBeforeStart) ||
                    // 3rd priority is the next due exercise
                    relevantExercises.sort((a, b) => {
                        return moment(a.dueDate).valueOf() - moment(b.dueDate).valueOf();
                    })[0];
            }
            this.nextRelevantCourse = relevantExercise.course!;
            return relevantExercise;
        }
    }

    /**
     * TODO: this code is currently unused: instead use a high priority notification and display a notification to the students in the notification center
     */
    subscribeForQuizStartForCourses() {
        if (this.courses) {
            // subscribe to quiz exercises that are live
            if (!this.quizExercisesChannels) {
                this.quizExercisesChannels = this.courses.map((course) => {
                    return '/topic/courses/' + course.id + '/quizExercises';
                });
                // quizExercises channels => react to the start of a quiz exercise for all courses
                this.quizExercisesChannels.forEach((channel) => this.jhiWebsocketService.subscribe(channel));
                this.quizExercisesChannels.forEach((channel) =>
                    this.jhiWebsocketService.receive(channel).subscribe(
                        (quizExercise: QuizExercise) => {
                            // TODO: conversion to moment is missing for exercise dates
                            if (quizExercise.started) {
                                // ignore set visible
                                this.isQuizLive = true;
                                this.liveQuiz = quizExercise;
                                this.liveQuizCourse = quizExercise.course!;
                            } else if (quizExercise.visibleToStudents) {
                                // TODO: show the exercise at the top
                            }
                        },
                        () => {},
                    ),
                );
            }
        }
    }
}
