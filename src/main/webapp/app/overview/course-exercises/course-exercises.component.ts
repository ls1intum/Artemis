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
    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;

    course?: Course;
    courseId: number;
    sortedExercises?: Exercise[];
    exerciseForGuidedTour?: Exercise;

    exerciseSelected: boolean = true;
    accordionExerciseGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sidebarData: SidebarData;
    sidebarExercises: SidebarCardElement[] = [];
    isCollapsed: boolean = false;

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private guidedTourService: GuidedTourService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private router: Router,
        private courseOverviewService: CourseOverviewService,
    ) {}

    ngOnInit() {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('exercise');
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
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

        // If no exercise is selected navigate to the lastSelected or upcoming exercise
        this.navigateToExercise();
    }

    navigateToExercise() {
        const upcomingExercise = this.courseOverviewService.getUpcomingExercise(this.course?.exercises);
        const lastSelectedExercise = this.getLastSelectedExercise();
        const exerciseId = this.route.firstChild?.snapshot?.params.exerciseId;
        if (!exerciseId && lastSelectedExercise) {
            this.router.navigate([lastSelectedExercise], { relativeTo: this.route, replaceUrl: true });
        } else if (!exerciseId && upcomingExercise) {
            this.router.navigate([upcomingExercise.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            this.exerciseSelected = exerciseId ? true : false;
        }
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('exercise', this.isCollapsed);
    }

    getLastSelectedExercise(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.exercise.byCourse.' + this.courseId);
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
    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToExercise();
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }
}
