import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'ngx-webstorage';
import { User } from 'app/core/user/user.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';

interface PostVote {
    isPositive: boolean;
}

@Component({
    selector: 'jhi-post-votes',
    templateUrl: './post-votes.component.html',
    styleUrls: ['./post-votes.scss'],
})
export class PostVotesComponent implements OnInit, OnChanges {
    @Input() post: Post;

    user: User;
    userVote: PostVote | null;
    voteValueChange = 0;

    constructor(private localStorage: LocalStorageService, private accountService: AccountService, private metisService: MetisService) {}

    /**
     * load user's vote
     */
    ngOnInit(): void {
        this.updateVotes();
    }

    ngOnChanges(): void {
        this.updateVotes();
    }

    private updateVotes() {
        this.user = this.metisService.getUser();
        this.userVote = this.localStorage.retrieve(`q${this.post.id}u${this.user.id}`);
    }

    /**
     * toggle upvote
     */
    toggleUpVote(): void {
        if (this.userVote) {
            if (this.userVote.isPositive) {
                this.userVote = null;
                this.voteValueChange = -1;
                this.localStorage.clear(`q${this.post.id}u${this.user.id}`);
            } else {
                this.userVote.isPositive = true;
                this.voteValueChange = 2;
                this.localStorage.store(`q${this.post.id}u${this.user.id}`, this.userVote);
            }
        } else {
            this.userVote = { isPositive: true };
            this.voteValueChange = 1;
            this.localStorage.store(`q${this.post.id}u${this.user.id}`, this.userVote);
        }
        this.metisService.updatePostVotes(this.post, this.voteValueChange);
    }

    /**
     * toggle downvote
     */
    toggleDownVote(): void {
        if (this.userVote) {
            if (this.userVote.isPositive) {
                this.userVote.isPositive = false;
                this.voteValueChange = -2;
                this.localStorage.store(`q${this.post.id}u${this.user.id}`, this.userVote);
            } else {
                this.userVote = null;
                this.voteValueChange = 1;
                this.localStorage.clear(`q${this.post.id}u${this.user.id}`);
            }
        } else {
            this.userVote = { isPositive: false };
            this.voteValueChange = -1;
            this.localStorage.store(`q${this.post.id}u${this.user.id}`, this.userVote);
        }
        this.metisService.updatePostVotes(this.post, this.voteValueChange);
    }
}
