import { Component, Input, OnInit } from '@angular/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { Subscription, forkJoin } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-course-learning-goals',
    templateUrl: './course-learning-goals.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLearningGoalsComponent implements OnInit {
    @Input()
    courseId: number;

    isLoading = false;
    course?: Course;
    learningGoals: LearningGoal[] = [];
    prerequisites: LearningGoal[] = [];

    isCollapsed = true;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    private courseUpdateSubscription?: Subscription;

    constructor(
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private courseStorageService: CourseStorageService,
        private learningGoalService: LearningGoalService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.setCourse(this.courseStorageService.getCourse(this.courseId));
        this.courseUpdateSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course) => this.setCourse(course));
    }

    private setCourse(course?: Course) {
        this.course = course;
        if (this.course && this.course.learningGoals && this.course.prerequisites) {
            this.learningGoals = this.course.learningGoals;
            this.prerequisites = this.course.prerequisites;
        } else {
            this.loadData();
        }
    }

    get countLearningGoals() {
        return this.learningGoals.length;
    }

    get countMasteredLearningGoals() {
        return this.learningGoals.filter((lg) => {
            if (lg.userProgress?.length && lg.masteryThreshold) {
                return lg.userProgress.first()!.progress == 100 && lg.userProgress.first()!.confidence! >= lg.masteryThreshold!;
            }
            return false;
        }).length;
    }

    get countPrerequisites() {
        return this.prerequisites.length;
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadData() {
        this.isLoading = true;
        forkJoin([this.learningGoalService.getAllForCourse(this.courseId), this.learningGoalService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([learningGoals, prerequisites]) => {
                this.learningGoals = learningGoals.body!;
                this.prerequisites = prerequisites.body!;
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Calculates a unique identity for each competency card shown in the component
     * @param index The index in the list
     * @param learningGoal The competency of the current iteration
     */
    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }
}
