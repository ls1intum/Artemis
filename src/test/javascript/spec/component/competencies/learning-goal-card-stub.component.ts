import { Component, Input } from '@angular/core';
import { Competency } from 'app/entities/competency.model';

@Component({ selector: 'jhi-learning-goal-card', template: '<div><ng-content></ng-content></div>' })
export class LearningGoalCardStubComponent {
    @Input() courseId?: number;
    @Input() learningGoal: Competency;
    @Input() isPrerequisite: boolean;
    @Input() displayOnly: boolean;
}
