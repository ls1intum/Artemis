import { Component, Input, OnInit } from '@angular/core';
import { LearningGoal } from 'app/entities/learning-goal.model';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['./learning-goal-card.component.scss'],
})
export class LearningGoalCardComponent implements OnInit {
    @Input()
    learningGoal: LearningGoal;

    selectedTab: 'GOAL' | 'EXERCISES' | 'LECTURES' = 'GOAL';

    constructor() {}

    ngOnInit(): void {}
}
