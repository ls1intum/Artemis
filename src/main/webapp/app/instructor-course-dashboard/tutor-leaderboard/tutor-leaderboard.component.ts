import { Component, Input } from '@angular/core';
import { TutorLeaderboardElement } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.model';

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html',
})
export class TutorLeaderboardComponent {
    @Input() public tutorsData: TutorLeaderboardElement[] = [];
    @Input() public courseId?: number;
    @Input() public exerciseId?: number;

    sortPredicate = 'points';
    reverseOrder = false;

    callback() {}
}
