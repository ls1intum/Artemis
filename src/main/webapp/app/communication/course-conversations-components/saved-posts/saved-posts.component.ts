import { Component, effect, inject, input, output } from '@angular/core';
import { Posting, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { SavedPostService } from 'app/communication/service/saved-post.service';
import { faBookmark, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingSummaryComponent } from 'app/communication/course-conversations-components/posting-summary/posting-summary.component';

@Component({
    selector: 'jhi-saved-posts',
    templateUrl: './saved-posts.component.html',
    styleUrls: ['./saved-posts.component.scss'],
    imports: [TranslateDirective, FaIconComponent, PostingSummaryComponent],
})
export class SavedPostsComponent {
    savedPostStatus = input.required<SavedPostStatus>();
    courseId = input.required<number>();

    readonly onNavigateToPost = output<Posting>();

    private readonly savedPostService = inject(SavedPostService);

    protected posts: Posting[];
    protected hiddenPosts: number[] = [];
    protected isShowDeleteNotice = false;

    // Icons
    readonly faBookmark = faBookmark;
    readonly faInfoCircle = faInfoCircle;

    constructor() {
        effect(() => {
            this.isShowDeleteNotice = this.savedPostStatus() !== SavedPostStatus.IN_PROGRESS;

            this.savedPostService.fetchSavedPosts(this.courseId(), this.savedPostStatus()).subscribe({
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

    protected removeSavedPost(post: Posting) {
        this.savedPostService.removeSavedPost(post).subscribe({
            next: () => {},
        });
        this.hiddenPosts.push(post.id!);
    }

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
