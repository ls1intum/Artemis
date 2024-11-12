import { Component, effect, inject, input, output } from '@angular/core';
import { Posting, SavedPostStatus, SavedPostStatusMap } from 'app/entities/metis/posting.model';
import { SavedPostService } from 'app/shared/metis/saved-post.service';
import { faBookmark, faInfoCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-saved-posts',
    templateUrl: './saved-posts.component.html',
    styleUrls: ['./saved-posts.component.scss'],
})
export class SavedPostsComponent {
    readonly savedPostStatus = input<SavedPostStatus>();
    readonly courseId = input<number>();

    readonly onNavigateToPost = output<Posting>();

    private readonly savedPostService = inject(SavedPostService);

    protected posts: Posting[];
    protected hiddenPosts: number[] = [];
    protected savedPostStatusMap: SavedPostStatusMap = SavedPostStatusMap.PROGRESS;
    protected isShowDeleteNotice: boolean = false;

    // Icons
    readonly faBookmark = faBookmark;
    readonly faInfoCircle = faInfoCircle;

    constructor() {
        effect(() => {
            this.savedPostStatusMap = Posting.statusToMap(this.savedPostStatus()!);

            this.isShowDeleteNotice = this.savedPostStatusMap !== SavedPostStatusMap.PROGRESS;

            this.savedPostService.fetchSavedPosts(this.courseId()!, this.savedPostStatus()!).subscribe({
                next: (response) => {
                    if (!response.body) {
                        this.posts = [];
                    } else {
                        this.posts = response.body.map(this.savedPostService.convertPostingToCorrespondingType);
                    }
                },
                error: () => {
                    this.posts = [];
                },
                complete: () => {
                    this.hiddenPosts = [];
                },
            });
        });
    }

    protected trackPostFunction = (index: number, post: Posting): string => index + '' + post.id!;

    protected changeSavedPostStatus(post: Posting, status: SavedPostStatus) {
        this.savedPostService.changeSavedPostStatus(post, status).subscribe({
            next: () => {},
        });
        this.hiddenPosts.push(post.id!);
    }

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
