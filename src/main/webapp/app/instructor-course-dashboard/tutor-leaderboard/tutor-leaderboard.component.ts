import { User } from 'app/core';
import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';

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
export class TutorLeaderboardComponent implements OnInit, OnChanges {
    @Input() public tutorsData: TutorLeaderboardData = {};
    sortedTutorData: TutorLeaderboardElement[] = [];

    ngOnInit() {
        this.sortTutorData();
    }

    ngOnChanges(changes: SimpleChanges): void {
        this.sortTutorData();
    }

    private sortTutorData() {
        this.sortedTutorData = Object.values(this.tutorsData);
        this.sortedTutorData = this.sortedTutorData.sort((firstElement, secondElement) => secondElement.numberOfAssessments - firstElement.numberOfAssessments);
    }
}
