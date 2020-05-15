import { Component, OnInit, EventEmitter, Input, Output } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import { User } from 'app/core/user/user.model';

export interface StudentVotesAction {
    name: StudentVotesActionName;
    value: number;
}

interface StudentVote {
    isPositive: boolean;
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
export class StudentVotesComponent implements OnInit {
    @Input() user: User;
    @Input() questionId: number;
    @Input() votes: number;
    @Output() interactVotes = new EventEmitter<StudentVotesAction>();

    private userVote: StudentVote | null;

    constructor(private localStorage: LocalStorageService) {}

    /**
     * load user's vote
     */
    ngOnInit(): void {
        if (this.questionId && this.user) {
            this.userVote = this.localStorage.retrieve(`q${this.questionId}u${this.user.id}`);
        }
    }

    /**
     * toggle upvote
     */
    toggleUpVote(): void {
        if (this.userVote) {
            if (this.userVote.isPositive) {
                this.votes--;
                this.userVote = null;
                this.localStorage.clear(`q${this.questionId}u${this.user.id}`);
            } else {
                this.votes++;
                this.userVote.isPositive = true;
                this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
            }
        } else {
            this.votes++;
            this.userVote = { isPositive: true };
            this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
        }
        this.interactVotes.emit({
            name: StudentVotesActionName.VOTE_CHANGE,
            value: this.votes,
        });
    }

    /**
     * toggle downvote
     */
    toggleDownVote(): void {
        if (this.userVote) {
            if (this.userVote.isPositive) {
                this.votes--;
                this.userVote.isPositive = false;
                this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
            } else {
                this.votes++;
                this.userVote = null;
                this.localStorage.clear(`q${this.questionId}u${this.user.id}`);
            }
        } else {
            this.votes--;
            this.userVote = { isPositive: false };
            this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
        }
        this.interactVotes.emit({
            name: StudentVotesActionName.VOTE_CHANGE,
            value: this.votes,
        });
    }
}
