import { Component, OnInit, EventEmitter, Input, Output } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
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
    @Input() questionId: number;
    @Input() votes: number;
    @Output() interactVotes = new EventEmitter<StudentVotesAction>();

    user: User;
    userVote: StudentVote | null;
    voteValueChange = 0;

    constructor(private localStorage: LocalStorageService, private accountService: AccountService) {}

    /**
     * load user's vote
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.user = user;
            this.userVote = this.localStorage.retrieve(`q${this.questionId}u${this.user.id}`);
        });
    }

    /**
     * toggle upvote
     */
    toggleUpVote(): void {
        if (this.userVote) {
            if (this.userVote.isPositive) {
                this.userVote = null;
                this.voteValueChange = -1;
                this.localStorage.clear(`q${this.questionId}u${this.user.id}`);
            } else {
                this.userVote.isPositive = true;
                this.voteValueChange = 2;
                this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
            }
        } else {
            this.userVote = { isPositive: true };
            this.voteValueChange = 1;
            this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
        }
        this.interactVotes.emit({
            name: StudentVotesActionName.VOTE_CHANGE,
            value: this.voteValueChange,
        });
    }

    /**
     * toggle downvote
     */
    toggleDownVote(): void {
        if (this.userVote) {
            if (this.userVote.isPositive) {
                this.userVote.isPositive = false;
                this.voteValueChange = -2;
                this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
            } else {
                this.userVote = null;
                this.voteValueChange = 1;
                this.localStorage.clear(`q${this.questionId}u${this.user.id}`);
            }
        } else {
            this.userVote = { isPositive: false };
            this.voteValueChange = -1;
            this.localStorage.store(`q${this.questionId}u${this.user.id}`, this.userVote);
        }
        this.interactVotes.emit({
            name: StudentVotesActionName.VOTE_CHANGE,
            value: this.voteValueChange,
        });
    }
}
