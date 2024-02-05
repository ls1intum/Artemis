import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise } from 'app/entities/exercise.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesComponent implements OnInit, OnDestroy {
    private courseId: number;
    private paramSubscription: Subscription;
    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;

    course?: Course;
    exerciseForGuidedTour?: Exercise;

    exerciseSelected: boolean = true;
    exerciseId: number;

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private guidedTourService: GuidedTourService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.onCourseLoad();
        });

        this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, courseExerciseOverviewTour, true);
        const upcomingExercise = this.getUpcomingExercise();
        this.paramSubscription = this.route.params.subscribe((params) => {
            this.exerciseId = parseInt(params['exerciseId'], 10);
            // If no exercise is selected navigate to the upcoming exercise
            if (!this.exerciseId && upcomingExercise) {
                this.router.navigate([upcomingExercise.id], { relativeTo: this.route });
            } else {
                this.exerciseSelected = this.exerciseId ? true : false;
            }
        });
    }

    getUpcomingExercise(): Exercise | undefined {
        if (this.course) {
            const upcomingExercise = this.course.exercises?.reduce((a, b) => {
                return (a?.dueDate?.valueOf() ?? 0) > (b?.dueDate?.valueOf() ?? 0) ? a : b;
            });
            return upcomingExercise;
        }
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this.course!.exercises, true);
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }
}
