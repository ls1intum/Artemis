import { User } from 'app/core';
import { Component, Input } from '@angular/core';
import { KeyValue } from '@angular/common';

interface TutorLeaderboardElement {
    tutor: User;
    numberOfAssessments: number;
    numberOfComplaints: number;
}

export interface TutorLeaderboardData {
    [key: string]: TutorLeaderboardElement;
}

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html',
})
export class TutorLeaderboardComponent {
    @Input() public tutorsData: TutorLeaderboardData = {};

    orderByNumberOfAssessments(firstElement: KeyValue<number, TutorLeaderboardElement>, secondElement: KeyValue<number, TutorLeaderboardElement>) {
        return secondElement.value.numberOfAssessments - firstElement.value.numberOfAssessments;
    }
}
