import { Component, Input } from '@angular/core';
import { KeyValue } from '@angular/common';

export interface TutorLeaderboardElement {
    name: string;
    login: string;
    numberOfAssessments: number;
    numberOfComplaints: number;
    tutorId: number;
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
    @Input() public courseId?: number;
    @Input() public exerciseId?: number;

    orderByNumberOfAssessments(firstElement: KeyValue<number, TutorLeaderboardElement>, secondElement: KeyValue<number, TutorLeaderboardElement>) {
        return secondElement.value.numberOfAssessments - firstElement.value.numberOfAssessments;
    }
}
