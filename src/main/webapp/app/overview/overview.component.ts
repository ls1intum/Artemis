import { Component } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Exercise, ExerciseService } from 'app/entities/exercise';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: []
})
export class OverviewComponent {
    public courses: Course[];
    public nextRelevantCourse: Course;

    constructor(private courseService: CourseService, private exerciseService: ExerciseService, private jhiAlertService: JhiAlertService, private  courseScoreCalculationService: CourseScoreCalculationService) {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
                this.courseScoreCalculationService.setCourses(this.courses);
            },
            (response: string) => this.onError(response)
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    get nextRelevantExercise(): Exercise {
        let relevantExercise: Exercise = null;
        if (this.courses) {
            this.courses.forEach(course => {
                let relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
                if (relevantExerciseForCourse) {
                    if (!relevantExercise) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    } else if (relevantExerciseForCourse.dueDate.isBefore(relevantExercise.dueDate)) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    }
                }
            });
        }
        return relevantExercise;
    }

}
