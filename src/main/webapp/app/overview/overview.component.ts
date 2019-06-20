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
        tourId: 'course-overview-tour',
        useOrb: false,
        steps: [
            {
                title: 'Welcome to Artemis',
                content: 'Artemis is a learning platform where you can solve programming exercises in interactive learning sessions.',
            },
            {
                title: 'Course overview',
                selector: '#overview-menu',
                content: 'On this page you can see an overview of all courses which you are signed up for.',
                orientation: Orientation.BottomLeft,
            },
            {
                title: 'Your current courses',
                selector: '.card-header',
                content: 'This is a course you are signed up to. Please check out the course details afterwards by clicking on the panel.',
                orientation: Orientation.Right,
            },
            {
                title: 'Next due exercise',
                selector: '.card-footer',
                content: 'See what exercise has to be completed in the next few days.',
                orientation: Orientation.Right,
            },
            {
                title: 'Sign up for other courses',
                selector: 'jhi-course-registration-selector button',
                content: 'You can click here and see whether you can sign up for other courses additionally.',
                useHighlightPadding: true,
                highlightPadding: 10,
                orientation: Orientation.Left,
            },
            {
                title: 'Your notifications',
                selector: '#notificationsNavBarDropdown',
                content: 'Get notified about new exercises from this notification dropdown.',
                useHighlightPadding: true,
                highlightPadding: 10,
                orientation: Orientation.Left,
            },
            {
                title: 'Personal settings',
                selector: '#account-menu',
                content: 'In your account menu you can change your display language or log out from Artemis.',
                useHighlightPadding: true,
                highlightPadding: 10,
                orientation: Orientation.Left,
            },
            {
                title: 'Contact the Artemis team',
                selector: '.footer .col-sm-6',
                content: 'Feel free to contact us to leave us some feedback, request features or to report bugs in the application.',
                orientation: Orientation.TopLeft,
            },
            {
                title: 'Restart tour',
                selector: '.guided-tour-button',
                content: 'Just click on this button if you want to restart the guided tour at any other time.',
                useHighlightPadding: true,
                highlightPadding: 5,
                orientation: Orientation.Left,
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
        setTimeout(() => {
            this.startTour();
        }, 1500);
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
