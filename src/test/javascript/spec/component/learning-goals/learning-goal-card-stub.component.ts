import { Component, Input } from '@angular/core';
import { LearningGoal } from 'app/entities/learningGoal.model';

@Component({ selector: 'jhi-learning-goal-card', template: '<div><ng-content></ng-content></div>' })
export class LearningGoalCardStubComponent {
    @Input() courseId?: number;
    @Input() learningGoal: LearningGoal;
    @Input() isPrerequisite: boolean;
    @Input() displayOnly: boolean;
}
