import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LearningGoal, LearningGoalProgress, getIcon, getIconTooltip } from 'app/entities/learningGoal.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-course-learning-goals-details',
    templateUrl: './course-learning-goals-details.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLearningGoalsDetailsComponent implements OnInit {
    learningGoalId?: number;
    courseId?: number;
    isLoading = false;
    learningGoal: LearningGoal;

    readonly LectureUnitType = LectureUnitType;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(private alertService: AlertService, private activatedRoute: ActivatedRoute, private learningGoalService: LearningGoalService) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.learningGoalId = +params['learningGoalId'];
            this.courseId = +params['courseId'];
            if (this.learningGoalId && this.courseId) {
                this.loadData();
            }
        });
    }

    private loadData() {
        this.isLoading = true;
        this.learningGoalService.findById(this.learningGoalId!, this.courseId!).subscribe({
            next: (resp) => {
                this.learningGoal = resp.body!;
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    getUserProgress(): LearningGoalProgress {
        if (this.learningGoal.userProgress?.length) {
            return this.learningGoal.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as LearningGoalProgress;
    }

    get progress(): number {
        // The percentage of completed lecture units and participated exercises
        return this.getUserProgress().progress ?? 0;
    }

    get confidence(): number {
        // Confidence level (average score in exercises) in proportion to the threshold value
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return ((this.getUserProgress().confidence ?? 0) / (this.learningGoal.masteryThreshold ?? 100)) * 100;
    }

    get mastery(): number {
        // Advancement towards mastery as a weighted function of progress and confidence
        const weight = 2 / 3;
        return (1 - weight) * this.progress + weight * this.confidence;
    }
}
