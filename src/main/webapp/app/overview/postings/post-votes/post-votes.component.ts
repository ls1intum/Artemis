import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'ngx-webstorage';
import { User } from 'app/core/user/user.model';

export interface PostVotesAction {
    name: PostVotesActionName;
    value: number;
}

interface PostVote {
    isPositive: boolean;
}

/**
 * Names for interacting with parent component
 * @enum { number }
 */
export enum PostVotesActionName {
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-post-votes',
    templateUrl: './post-votes.component.html',
    styleUrls: ['../postings.scss'],
})
export class PostVotesComponent implements OnInit {
    @Input() postId: number;
    @Input() votes: number;
    @Output() interactVotes = new EventEmitter<PostVotesAction>();

    user: User;
    userVote: PostVote | null;
    voteValueChange = 0;

    constructor(private localStorage: LocalStorageService, private accountService: AccountService) {}

    /**
     * load user's vote
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.user = user;
            this.userVote = this.localStorage.retrieve(`q${this.postId}u${this.user.id}`);
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
                this.localStorage.clear(`q${this.postId}u${this.user.id}`);
            } else {
                this.userVote.isPositive = true;
                this.voteValueChange = 2;
                this.localStorage.store(`q${this.postId}u${this.user.id}`, this.userVote);
            }
        } else {
            this.userVote = { isPositive: true };
            this.voteValueChange = 1;
            this.localStorage.store(`q${this.postId}u${this.user.id}`, this.userVote);
        }
        this.interactVotes.emit({
            name: PostVotesActionName.VOTE_CHANGE,
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
                this.localStorage.store(`q${this.postId}u${this.user.id}`, this.userVote);
            } else {
                this.userVote = null;
                this.voteValueChange = 1;
                this.localStorage.clear(`q${this.postId}u${this.user.id}`);
            }
        } else {
            this.userVote = { isPositive: false };
            this.voteValueChange = -1;
            this.localStorage.store(`q${this.postId}u${this.user.id}`, this.userVote);
        }
        this.interactVotes.emit({
            name: PostVotesActionName.VOTE_CHANGE,
            value: this.voteValueChange,
        });
    }
}
