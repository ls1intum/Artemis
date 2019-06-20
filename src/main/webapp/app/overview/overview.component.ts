import { Component } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { AccountService } from 'app/core';
import { cloneDeep } from 'lodash';
import { GuidedTour, Orientation } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: [],
})
export class OverviewComponent {
    public courses: Course[];
    public nextRelevantCourse: Course;

    public overviewTour: GuidedTour = {
        tourId: 'overview-tour',
        useOrb: false,
        steps: [
            {
                title: 'Welcome to Artemis',
                selector: '.navbar-brand',
                content: "<p>Please click on this <a href='#'>link</a></p>",
                orientation: Orientation.Right,
            },
            {
                title: 'Course Overview',
                selector: '#overview-menu',
                content: 'On this page you can see an overview of all courses which you are signed up for.',
                orientation: Orientation.BottomLeft,
            },
            {
                title: 'Course Overview',
                selector: '#course-admin-menu',
                content: 'Navigate here to see the course administration',
                orientation: Orientation.Bottom,
            },
            {
                title: 'General page step',
                content: 'We have the concept of general page steps so that a you can introduce a user to a page or non specific instructions',
            },
        ],
    };

    constructor(
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private guidedTourService: GuidedTourService,
    ) {
        this.loadAndFilterCourses();
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
        this.guidedTourService.startTour(this.overviewTour);
    }
}
