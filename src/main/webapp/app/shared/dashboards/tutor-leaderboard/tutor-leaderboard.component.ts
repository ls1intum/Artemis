import { Component, Input, OnInit } from '@angular/core';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html',
})
export class TutorLeaderboardComponent implements OnInit {
    @Input() public tutorsData: TutorLeaderboardElement[] = [];
    @Input() public course?: Course;
    @Input() public exercise?: Exercise;
    @Input() public exam?: Exam;

    isAtLeastInstructor = false;

    isExamMode = false;
    sortPredicate = 'points';
    reverseOrder = false;

    constructor(private accountService: AccountService, private sortService: SortService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        if (this.course) {
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);
        }
        if (this.exercise && this.exercise.course) {
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course);
        }
        if (this.exam) {
            this.isExamMode = true;
        }
        this.sortRows();
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorsData, this.sortPredicate, this.reverseOrder);
    }
}
