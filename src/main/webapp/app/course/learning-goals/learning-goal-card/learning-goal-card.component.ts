import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { LearningGoal } from 'app/entities/learningGoal.model';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['./learning-goal-card.component.scss'],
})
export class LearningGoalCardComponent implements OnInit, OnChanges {
    @Input()
    learningGoal: LearningGoal;
    descriptionShown: string | undefined;
    truncatedDescription: string | undefined = undefined;
    noOfCharactersBeforeTruncating = 280;

    constructor() {}

    ngOnInit(): void {}

    ngOnChanges(): void {
        if (this.learningGoal && this.learningGoal.description) {
            if (this.learningGoal.description.length > this.noOfCharactersBeforeTruncating) {
                this.truncatedDescription = this.learningGoal.description.substr(0, this.noOfCharactersBeforeTruncating - 1) + '\u2026';
                this.descriptionShown = this.truncatedDescription;
            } else {
                this.truncatedDescription = undefined;
                this.descriptionShown = this.learningGoal.description;
            }
        }
    }

    expandCollapseDescription() {
        if (this.descriptionShown === this.truncatedDescription) {
            this.descriptionShown = this.learningGoal.description;
        } else {
            this.descriptionShown = this.truncatedDescription;
        }
    }
}
