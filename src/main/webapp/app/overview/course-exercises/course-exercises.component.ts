import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise } from 'app/entities/exercise.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { AccordionGroups, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { CourseOverviewService } from '../course-overview.service';

//Todo einheitlöich machen
const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

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
    sortedExercises?: Exercise[];
    exerciseForGuidedTour?: Exercise;

    exerciseSelected: boolean = true;
    accordionExerciseGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sidebarData: SidebarData;
    sidebarExercises: SidebarCardElement[] = [];

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private guidedTourService: GuidedTourService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private router: Router,
        private courseOverviewService: CourseOverviewService,
    ) {}

    ngOnInit() {
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params.courseId, 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.onCourseLoad();
        this.prepareSidebarData();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.prepareSidebarData();
            this.onCourseLoad();
        });

        this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, courseExerciseOverviewTour, true);
        const upcomingExercise = this.courseOverviewService.getUpcomingExercise(this.course?.exercises);
        this.paramSubscription = this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params.exerciseId, 10);
            // If no exercise is selected navigate to the upcoming exercise
            if (!exerciseId && upcomingExercise) {
                this.router.navigate([upcomingExercise.id], { relativeTo: this.route });
            } else {
                this.exerciseSelected = exerciseId ? true : false;
            }
        });
    }

    prepareSidebarData() {
        if (!this.course?.exercises) {
            return;
        }
        this.sortedExercises = this.courseOverviewService.sortExercises(this.course.exercises);
        this.sidebarExercises = this.courseOverviewService.mapExercisesToSidebarCardElements(this.sortedExercises);
        this.accordionExerciseGroups = this.courseOverviewService.groupExercisesByDueDate(this.sortedExercises);
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exercise',
            storageId: 'exercise',
            groupedData: this.accordionExerciseGroups,
            ungroupedData: this.sidebarExercises,
        };
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this.course?.exercises, true);
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }
}
