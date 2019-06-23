import { Component } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { AccountService } from 'app/core';
import { cloneDeep } from 'lodash';
import { GuidedTour } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: [],
})
export class OverviewComponent {
    public courses: Course[];
    public nextRelevantCourse: Course;
    public overviewTour: GuidedTour;

    constructor(
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private guidedTourService: GuidedTourService,
    ) {
        this.loadAndFilterCourses();
        setTimeout(() => {
            this.startTour();
        }, 500);
    }

    loadAndFilterCourses() {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
                this.courseScoreCalculationService.setCourses(this.courses);
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    get nextRelevantExercise(): Exercise {
        let relevantExercise: Exercise = null;
        if (this.courses) {
            this.courses.forEach(course => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
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

    public startTour(): void {
        this.guidedTourService.getOverviewTour().subscribe(tour => {
            this.overviewTour = tour;
            this.guidedTourService.startTour(this.overviewTour);
        });
    }
}
