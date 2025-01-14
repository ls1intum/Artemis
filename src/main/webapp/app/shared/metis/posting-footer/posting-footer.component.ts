import { AfterContentChecked, ChangeDetectorRef, Component, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild, ViewContainerRef, inject, input, output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { Posting } from 'app/entities/metis/posting.model';
import { AnswerPostComponent } from '../answer-post/answer-post.component';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

interface PostGroup {
    author: User | undefined;
    posts: AnswerPost[];
}

@Component({
    selector: 'jhi-posting-footer',
    templateUrl: './posting-footer.component.html',
    imports: [AnswerPostComponent, AnswerPostCreateEditModalComponent, ArtemisTranslatePipe],
})
export class PostingFooterComponent implements OnInit, OnDestroy, AfterContentChecked, OnChanges {
    lastReadDate = input<dayjs.Dayjs | undefined>();
    readOnlyMode = input<boolean>(false);
    previewMode = input<boolean>(false);
    modalRef = input<NgbModalRef | undefined>();
    hasChannelModerationRights = input<boolean>(false);
    showAnswers = input<boolean>(false);
    isCommunicationPage = input<boolean>(false);
    sortedAnswerPosts = input<AnswerPost[]>([]);
    isThreadSidebar = input<boolean>(false);
    posting = input<Posting>();

    // Output Signals
    openThread = output<void>();
    userReferenceClicked = output<string>();
    channelReferenceClicked = output<number>();

    @ViewChild(AnswerPostCreateEditModalComponent) answerPostCreateEditModal?: AnswerPostCreateEditModalComponent;
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef!: ViewContainerRef;
    @ViewChild('createAnswerPostModal') createAnswerPostModalComponent!: AnswerPostCreateEditModalComponent;

    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse = false;
    courseId!: number;
    groupedAnswerPosts: PostGroup[] = [];

    protected metisService: MetisService = inject(MetisService);
    protected changeDetector: ChangeDetectorRef = inject(ChangeDetectorRef);

    ngOnInit(): void {
        this.courseId = this.metisService.getCourse().id!;
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.createdAnswerPost = this.createEmptyAnswerPost();
        this.groupAnswerPosts();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['sortedAnswerPosts']) {
            this.groupAnswerPosts();
            this.changeDetector.detectChanges();
        }
    }

    ngOnDestroy(): void {
        this.answerPostCreateEditModal?.createEditAnswerPostContainerRef?.clear();
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the tag-selector
     * on dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.changeDetector.detectChanges();
    }

    /**
     * creates empty default answer post that is needed on initialization of a newly opened modal to edit or create an answer post, with accordingly set resolvesPost flag
     * @return AnswerPost created empty default answer post
     */
    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.posting();
        answerPost.resolvesPost = this.isAtLeastTutorInCourse;
        return answerPost;
    }

    groupAnswerPosts(): void {
        if (!this.sortedAnswerPosts() || this.sortedAnswerPosts().length === 0) {
            this.groupedAnswerPosts = [];
            return;
        }

        const sortedAnswerPosts = this.sortedAnswerPosts()
            .slice()
            .reverse()
            .map((post) => {
                (post as any).creationDateDayjs = post.creationDate ? dayjs(post.creationDate) : undefined;
                return post;
            });

        const sortedPosts = sortedAnswerPosts.sort((a, b) => {
            const aDate = (a as any).creationDateDayjs;
            const bDate = (b as any).creationDateDayjs;
            return aDate?.valueOf() - bDate?.valueOf();
        });

        const groups: PostGroup[] = [];
        let currentGroup: PostGroup = {
            author: sortedPosts[0].author,
            posts: [{ ...sortedPosts[0], isConsecutive: false }],
        };

        for (let i = 1; i < sortedPosts.length; i++) {
            const currentPost = sortedPosts[i];
            const lastPostInGroup = currentGroup.posts[currentGroup.posts.length - 1];

            const currentDate = (currentPost as any).creationDateDayjs;
            const lastDate = (lastPostInGroup as any).creationDateDayjs;

            let timeDiff = Number.MAX_SAFE_INTEGER;
            if (currentDate && lastDate) {
                timeDiff = currentDate.diff(lastDate, 'minute');
            }

            if (currentPost.author?.id === currentGroup.author?.id && timeDiff < 5 && timeDiff >= 0) {
                currentGroup.posts.push({ ...currentPost, isConsecutive: true }); // consecutive post
            } else {
                groups.push(currentGroup);
                currentGroup = {
                    author: currentPost.author,
                    posts: [{ ...currentPost, isConsecutive: false }],
                };
            }
        }

        groups.push(currentGroup);
        this.groupedAnswerPosts = groups;
        this.changeDetector.detectChanges();
    }

    trackGroupByFn(_: number, group: PostGroup): number {
        return group.posts[0].id!;
    }

    trackPostByFn(_: number, post: AnswerPost): number {
        return post.id!;
    }

    isLastPost(group: PostGroup, answerPost: AnswerPost): boolean {
        const lastPostInGroup = group.posts[group.posts.length - 1];
        return lastPostInGroup.id === answerPost.id;
    }

    /**
     * Open create answer modal
     */
    openCreateAnswerPostModal() {
        this.createAnswerPostModalComponent?.open();
    }

    /**
     * Close create answer modal
     */
    closeCreateAnswerPostModal() {
        this.createAnswerPostModalComponent?.close();
    }

    protected postsTrackByFn(_index: number, post: Post): number {
        return post.id!;
    }
}
