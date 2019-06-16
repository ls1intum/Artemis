import { Component, Input } from '@angular/core';

export interface TutorLeaderboardElement {
    name: string;
    login: string;
    numberOfAssessments: number;
    numberOfComplaints: number;
    tutorId: number;
}

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html',
})
export class TutorLeaderboardComponent {
    @Input() public tutorsData: TutorLeaderboardElement[] = [];
    @Input() public courseId?: number;
    @Input() public exerciseId?: number;

    sortPredicate = 'numberOfAssessments';
    reverseOrder = false;

    callback() {}
}
