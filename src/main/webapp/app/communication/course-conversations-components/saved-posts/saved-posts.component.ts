import { Component, OnDestroy, effect, inject, input, output, untracked } from '@angular/core';
import { Posting, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { SavedPostService } from 'app/communication/service/saved-post.service';
import { faBookmark, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingSummaryComponent } from 'app/communication/course-conversations-components/posting-summary/posting-summary.component';
import { Subscription, take } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-saved-posts',
    templateUrl: './saved-posts.component.html',
    styleUrls: ['./saved-posts.component.scss'],
    imports: [TranslateDirective, FaIconComponent, PostingSummaryComponent],
})
export class SavedPostsComponent implements OnDestroy {
    savedPostStatus = input.required<SavedPostStatus>();
    courseId = input.required<number>();

    readonly onNavigateToPost = output<Posting>();

    private readonly savedPostService = inject(SavedPostService);
    private readonly alertService = inject(AlertService);
    private fetchSubscription?: Subscription;

    protected posts: Posting[];
    protected hiddenPosts: number[] = [];
    protected isShowDeleteNotice = false;

    // Icons
    readonly faBookmark = faBookmark;
    readonly faInfoCircle = faInfoCircle;

    constructor() {
        effect(() => {
            const savedPostStatus = this.savedPostStatus();
            const courseId = this.courseId();
            untracked(() => {
                this.isShowDeleteNotice = savedPostStatus !== SavedPostStatus.IN_PROGRESS;

                this.fetchSubscription?.unsubscribe();
                this.fetchSubscription = this.savedPostService.fetchSavedPosts(courseId, savedPostStatus).subscribe({
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
        });
    }

    ngOnDestroy(): void {
        this.fetchSubscription?.unsubscribe();
    }

    protected trackPostFunction = (index: number, post: Posting): string => index + '' + post.id!;

    protected changeSavedPostStatus(post: Posting, status: SavedPostStatus) {
        this.savedPostService
            .changeSavedPostStatus(post, status)
            .pipe(take(1))
            .subscribe({
                next: () => this.hiddenPosts.push(post.id!),
                error: () => this.alertService.error('artemisApp.metis.post.changeSavedStatusError'),
            });
    }

    protected removeSavedPost(post: Posting) {
        this.savedPostService
            .removeSavedPost(post)
            .pipe(take(1))
            .subscribe({
                next: () => this.hiddenPosts.push(post.id!),
                error: () => this.alertService.error('artemisApp.metis.post.removeBookmarkError'),
            });
    }

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
