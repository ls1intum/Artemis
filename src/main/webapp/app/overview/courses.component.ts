import { Component, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { CoursesForDashboardDTO } from 'app/course/manage/courses-for-dashboard-dto';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/entities/exam.model';
import { Router } from '@angular/router';
import { faPenAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { CourseForDashboardDTO } from 'app/course/manage/course-for-dashboard-dto';

@Component({
    selector: 'jhi-overview',
    templateUrl: './courses.component.html',
    styleUrls: ['./courses.component.scss'],
})
export class CoursesComponent implements OnInit, OnChanges, OnDestroy {
    courses: Course[];
    public nextRelevantCourse?: Course;
    nextRelevantCourseForExam?: Course;
    nextRelevantExams?: Exam[];

    public recentlyAccessedCourses: Course[] = [];
    public regularCourses: Course[] = [];

    courseForGuidedTour?: Course;
    quizExercisesChannels: string[] = [];

    nextRelevantExercise?: Exercise;

    // Icons
    faPenAlt = faPenAlt;

    constructor(
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private guidedTourService: GuidedTourService,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
        private router: Router,
        private courseAccessStorageService: CourseAccessStorageService,
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
            next: (res: HttpResponse<CoursesForDashboardDTO>) => {
                if (res.body) {
                    const courses: Course[] = [];
                    res.body.courses.forEach((courseDto: CourseForDashboardDTO) => {
                        courses.push(courseDto.course);
                    });
                    this.courses = courses.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
                    this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseOverviewTour, true);

                    this.nextRelevantExams = res.body.activeExams ?? [];
                    this.nextRelevantExercise = this.findNextRelevantExercise();
                    this.sortCoursesInRecentlyAccessedAndRegularCourses();
                }
            },
        });
    }

    // TODO: we should remove this functionality or move it to the server
    findNextRelevantExercise() {
        const relevantExercises = new Array<Exercise>();
        let relevantExercise: Exercise | undefined;
        if (this.courses) {
            this.courses
                .filter((course) => !course.testCourse)
                .forEach((course) => {
                    const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises || []);
                    if (relevantExerciseForCourse) {
                        relevantExerciseForCourse.course = course;
                        relevantExercises.push(relevantExerciseForCourse);
                    }
                });
            if (!relevantExercises.length) {
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
     * Sorts the courses into recently accessed and regular courses.
     * If there are less than 5 courses, all courses are displayed in the regular courses section.
     */
    sortCoursesInRecentlyAccessedAndRegularCourses() {
        if (this.courses.length <= 5) {
            this.regularCourses = this.courses;
        } else {
            const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses();
            this.recentlyAccessedCourses = this.courses.filter((course) => lastAccessedCourseIds.includes(course.id!));
            this.regularCourses = this.courses.filter((course) => !lastAccessedCourseIds.includes(course.id!));
        }
    }

    /**
     * Sets the course for the next upcoming exam and returns the next upcoming exam or undefined
     */
    get nextRelevantExam(): Exam | undefined {
        // TODO: support multiple relevant exams in the future
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
