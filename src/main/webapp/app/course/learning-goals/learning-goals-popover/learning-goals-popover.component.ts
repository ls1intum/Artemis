import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { faFlag } from '@fortawesome/free-solid-svg-icons';
import { LearningGoal } from 'app/entities/learningGoal.model';

@Component({
    selector: 'jhi-learning-goals-popover',
    templateUrl: './learning-goals-popover.component.html',
    styleUrls: ['./learning-goals-popover.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class LearningGoalsPopoverComponent implements OnInit {
    @Input()
    courseId: number;
    @Input()
    learningGoals: LearningGoal[] = [];
    @Input()
    navigateTo: 'learningGoalManagement' | 'courseStatistics' = 'courseStatistics';

    navigationArray: string[] = [];

    // Icons
    faFlag = faFlag;

    constructor() {}

    ngOnInit(): void {
        if (this.courseId) {
            switch (this.navigateTo) {
                case 'courseStatistics': {
                    this.navigationArray = ['/courses', `${this.courseId}`, 'statistics'];
                    break;
                }
                case 'learningGoalManagement': {
                    this.navigationArray = ['/course-management', `${this.courseId}`, 'goal-management'];
                    break;
                }
            }
        }
    }
}
