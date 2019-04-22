import { User } from 'app/core';
import { Component, Input } from '@angular/core';

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
}
