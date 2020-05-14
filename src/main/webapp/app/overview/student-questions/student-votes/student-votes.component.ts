import { Component, EventEmitter, Input, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';

export interface StudentVotesAction {
    name: StudentVotesActionName;
    value: number;
}

/**
 * Names for interacting with parent component
 * @enum { number }
 */
export enum StudentVotesActionName {
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-student-votes',
    templateUrl: './student-votes.component.html',
    styleUrls: ['./../student-questions.scss'],
})
export class StudentVotesComponent {
    @Input() user: User;
    @Input() questionId: number;
    @Input() votes: number;
    @Output() interactVotes = new EventEmitter<StudentVotesAction>();

    /**
     * toggle upvote
     */
    toggleUpVote(): void {
        console.log('toggle upvote');
    }

    /**
     * toggle downvote
     */
    toggleDownVote(): void {
        console.log('toggleDownvote');
    }
}
