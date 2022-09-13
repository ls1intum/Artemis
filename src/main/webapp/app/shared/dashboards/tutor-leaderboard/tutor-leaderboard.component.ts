import { Component, Input, OnInit } from '@angular/core';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam.model';
import { faExclamationTriangle, faSort } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html',
})
export class TutorLeaderboardComponent implements OnInit {
    @Input() public tutorsData: TutorLeaderboardElement[] = [];
    @Input() public course?: Course;
    @Input() public exercise?: Exercise;
    @Input() public exam?: Exam;

    isExerciseDashboard = false;
    isExamMode = false;
    sortPredicate = 'points';
    reverseOrder = false;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;

    constructor(private accountService: AccountService, private sortService: SortService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        if (this.exercise && this.exercise.course) {
            this.course = this.exercise.course;
            this.isExerciseDashboard = true;
        }
        this.isExamMode = !!this.exam;
        this.sortRows();
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorsData, this.sortPredicate, this.reverseOrder);
    }
}
