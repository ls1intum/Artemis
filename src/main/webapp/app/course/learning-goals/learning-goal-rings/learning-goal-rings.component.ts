import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-learning-goal-rings',
    templateUrl: './learning-goal-rings.component.html',
    styleUrls: ['./learning-goal-rings.component.scss'],
})
export class LearningGoalRingsComponent {
    @Input() progress = 0;
    @Input() confidence = 0;
    @Input() mastery = 0;
}
