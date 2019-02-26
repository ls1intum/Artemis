import {User} from 'app/core';
import {Component, Input} from '@angular/core';

export interface TutorLeaderboardData {
    [key: string]: { tutor: User, nrOfAssessments: number };
}

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html'
})
export class TutorLeaderboardComponent {
    @Input() public tutorsData: TutorLeaderboardData = {};
}
