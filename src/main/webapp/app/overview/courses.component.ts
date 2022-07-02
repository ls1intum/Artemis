import { Component, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Router } from '@angular/router';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { faPenAlt } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-overview',
    templateUrl: './courses.component.html',
    styleUrls: ['./courses.component.scss'],
})
export class CoursesComponent implements OnInit, OnChanges, OnDestroy {
    public courses: Course[];
    public nextRelevantCourse?: Course;
    nextRelevantCourseForExam?: Course;
    nextRelevantExams?: Exam[];
    exams: Exam[] = [];

    courseForGuidedTour?: Course;
    quizExercisesChannels: string[] = [];

    nextRelevantExercise?: Exercise;

    // Icons
    faPenAlt = faPenAlt;

    constructor(
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private alertService: AlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private guidedTourService: GuidedTourService,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
        private examService: ExamManagementService,
        private router: Router,
        private serverDateService: ArtemisServerDateService,
    ) {}

    async ngOnInit() {
        this.loadAndFilterCourses();
        (await this.teamService.teamAssignmentUpdates).subscribe();
    }

    ngOnChanges() {
        this.nextRelevantExercise = this.findNextRelevantExercise();
    }

    /**
     * Unsubscribe from all websocket subscriptions.
     */
    ngOnDestroy() {
        if (this.quizExercisesChannels) {
            this.quizExercisesChannels.forEach((channel) => this.jhiWebsocketService.unsubscribe(channel));
        }
    }

    loadAndFilterCourses() {
        this.courseService.findAllForDashboard().subscribe({
            next: (res: HttpResponse<Course[]>) => {
                this.courses = res.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
                this.courseScoreCalculationService.setCourses(this.courses);
                this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseOverviewTour, true);

                // get all exams of courses
                this.courses.forEach((course) => {
                    if (course.exams) {
                        // set course for exam as it is not loaded within the server call
                        course.exams.forEach((exam) => {
                            exam.course = course;
                            this.exams.push(exam);
                        });
                    }
                });
                // Used as constant to limit the number of calls
                const timeNow = this.serverDateService.now();
                this.nextRelevantExams = this.exams.filter(
                    // TestExams should not be displayed as upcoming exams
                    (exam) => !exam.testExam! && timeNow.isBefore(exam.endDate!) && timeNow.isAfter(exam.visibleDate!),
                );
                this.nextRelevantExercise = this.findNextRelevantExercise();
            },
            error: (response: string) => this.onError(response),
        });
    }

    private onError(error: string) {
        this.alertService.error('error.unexpectedError', { error });
    }

    findNextRelevantExercise() {
        const relevantExercises = new Array<Exercise>();
        let relevantExercise: Exercise | undefined;
        if (this.courses) {
            this.courses.forEach((course) => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises || []);
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
                    relevantExercises.find((exercise: QuizExercise) => exercise.quizMode === QuizMode.SYNCHRONIZED && exercise.isActiveQuiz) ||
                    // 2nd priority is a visible quiz
                    relevantExercises.find((exercise: QuizExercise) => exercise.quizMode === QuizMode.SYNCHRONIZED && exercise.visibleToStudents) ||
                    // 3rd priority is the next due exercise
                    relevantExercises.sort((a, b) => {
                        return dayjs(a.dueDate).valueOf() - dayjs(b.dueDate).valueOf();
                    })[0];
            }
            this.nextRelevantCourse = relevantExercise.course!;
            return relevantExercise;
        }
    }

    /**
     * Sets the course for the next upcoming exam and returns the next upcoming exam or undefined
     */
    get nextRelevantExam(): Exam | undefined {
        let relevantExam: Exam | undefined;
        if (this.nextRelevantExams) {
            if (this.nextRelevantExams.length === 0) {
                return undefined;
            } else if (this.nextRelevantExams.length === 1) {
                relevantExam = this.nextRelevantExams[0];
            } else {
                relevantExam = this.nextRelevantExams.sort((a, b) => {
                    return dayjs(a.startDate).valueOf() - dayjs(b.startDate).valueOf();
                })[0];
            }
            this.nextRelevantCourseForExam = relevantExam.course!;
            return relevantExam;
        }
    }

    /**
     * navigate to /courses/:courseid/exams/:examId
     */
    openExam(): void {
        this.router.navigate(['courses', this.nextRelevantCourseForExam?.id, 'exams', this.nextRelevantExam!.id]);
    }
}
