import { Component, OnInit } from '@angular/core';
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

@Component({
    selector: 'jhi-overview',
    templateUrl: './courses.component.html',
    styles: [],
})
export class CoursesComponent implements OnInit {
    public courses: Course[];
    public nextRelevantCourse: Course;

    courseForGuidedTour: Course | null;

    constructor(
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private jhiAlertService: AlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private guidedTourService: GuidedTourService,
    ) {}

    loadAndFilterCourses() {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseScoreCalculationService.setCourses(this.courses);
                this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseOverviewTour, true);
            },
            (response: string) => this.onError(response),
        );
    }

    ngOnInit(): void {
        this.loadAndFilterCourses();
    }

    private onError(error: string) {
        this.jhiAlertService.error('error.unexpectedError', { error }, undefined);
    }

    get nextRelevantExercise(): Exercise | null {
        let relevantExercise: Exercise | null = null;
        if (this.courses) {
            this.courses.forEach((course) => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
                if (relevantExerciseForCourse) {
                    if (!relevantExercise) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    } else if (relevantExerciseForCourse.dueDate!.isBefore(relevantExercise.dueDate!)) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    }
                }
            });
        }
        return relevantExercise;
    }
}
