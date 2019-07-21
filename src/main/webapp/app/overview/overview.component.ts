import { Component, OnDestroy } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { AccountService } from 'app/core';
import { GuidedTour } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styleUrls: [],
})
export class OverviewComponent implements OnDestroy {
    public courses: Course[];
    public nextRelevantCourse: Course;
    public overviewTour: GuidedTour;

    subscription: Subscription;

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
            if (guidedTourService.guidedTourSettings && guidedTourService.guidedTourSettings.showCourseOverviewTour) {
                this.startTour();
            }
        }, 500);

        this.subscription = this.guidedTourService.getGuidedTourNotification().subscribe(component => {
            if (component && component.name === 'overview') {
                this.startTour();
                // this.guidedTourService.clearGuidedTourNotification();
            }
        });
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    loadAndFilterCourses() {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseScoreCalculationService.setCourses(this.courses);
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
    }

    get nextRelevantExercise(): Exercise | null {
        let relevantExercise: Exercise | null = null;
        if (this.courses) {
            this.courses.forEach(course => {
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

    /* Start guided tour for course overview page */
    public startTour(): void {
        this.guidedTourService.getOverviewTour().subscribe(tour => {
            this.overviewTour = tour;
            this.guidedTourService.startTour(this.overviewTour);
        });
    }
}
