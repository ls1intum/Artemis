import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit, Renderer2, effect, inject, input, model, output, signal, untracked, viewChild } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import { PostingDirective } from 'app/communication/directive/posting.directive';
import { MetisService } from 'app/communication/service/metis.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { ContextInformation, DisplayPriority, PageType, RouteComponents } from '../metis.util';
import { faBookmark, faBullhorn, faComments, faEnvelopeOpenText, faPencilAlt, faShare, faSmile, faThumbtack, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { PostingFooterComponent } from 'app/communication/posting-footer/posting-footer.component';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { DOCUMENT, NgClass, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingHeaderComponent } from '../posting-header/posting-header.component';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MessageInlineInputComponent } from '../message/message-inline-input/message-inline-input.component';
import { EmojiPickerComponent } from '../emoji/emoji-picker.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { PostingReactionsBarComponent } from 'app/communication/posting-reactions-bar/posting-reactions-bar.component';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { throwError } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ForwardedMessageComponent } from 'app/communication/forwarded-message/forwarded-message.component';
import { CourseWideSearchConfig } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss', './../metis.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        NgClass,
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        PostingHeaderComponent,
        RouterLinkActive,
        RouterLink,
        PostingContentComponent,
        PostingReactionsBarComponent,
        MessageInlineInputComponent,
        PostingFooterComponent,
        NgStyle,
        CdkOverlayOrigin,
        CdkConnectedOverlay,
        EmojiPickerComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ForwardedMessageComponent,
    ],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnDestroy {
    metisService = inject(MetisService);
    renderer = inject(Renderer2);
    private document = inject<Document>(DOCUMENT);

    lastReadDate = input<dayjs.Dayjs | undefined>(undefined);
    readOnlyMode = input<boolean>(false);
    previewMode = input<boolean>(false);
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post title
    modalRef = input<DynamicDialogRef | undefined>(undefined);
    searchConfig = input<CourseWideSearchConfig | undefined>(undefined);
    showAnswers = model<boolean>(false);

    openThread = output<void>();

    postFooterComponent = viewChild<PostingFooterComponent>('postFooter');
    reactionsBarComponent = viewChild.required<PostingReactionsBarComponent<Post>>(PostingReactionsBarComponent);

    static activeDropdownPost: PostComponent | undefined = undefined;

    showReactionSelector = false;
    displayInlineInput = signal(false);
    routerLink: RouteComponents;
    queryParams = {};
    readonly showAnnouncementIcon = signal(false);
    readonly showSearchResultInAnswersHint = signal(false);
    readonly sortedAnswerPosts = signal<AnswerPost[]>(undefined!);
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;

    readonly pageType = signal<PageType>(undefined!);
    readonly contextInformation = signal<ContextInformation>(undefined!);
    readonly PageType = PageType;
    readonly DisplayPriority = DisplayPriority;
    readonly mayEdit = signal(false);
    readonly mayDelete = signal(false);
    readonly canPin = signal(false);
    readonly canMarkAsUnread = signal(false);
    readonly originalPostDetails = signal<Post | AnswerPost | undefined>(undefined);
    readonly onNavigateToPost = output<Posting>();

    // Icons
    readonly faBullhorn = faBullhorn;
    readonly faComments = faComments;
    readonly faPencilAlt = faPencilAlt;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    readonly faThumbtack = faThumbtack;
    readonly faBookmark = faBookmark;
    readonly faShare = faShare;
    readonly faEnvelopeOpenText = faEnvelopeOpenText;

    isConsecutive = input<boolean>(false);
    forwardedPosts = input<(Post | undefined)[]>([]);
    forwardedAnswerPosts = input<(AnswerPost | undefined)[]>([]);
    readonly course = signal<Course>(undefined!);

    readonly hasOriginalPostBeenDeleted = signal<boolean>(undefined!);

    constructor() {
        super();
        this.course.set(this.metisService.getCourse() ?? throwError(() => new Error('Course not found')));
        // Reactive check: if forwarded post/answer is deleted, update flag
        effect(() => {
            const forwardedPosts = this.forwardedPosts();
            const forwardedAnswerPosts = this.forwardedAnswerPosts();
            untracked(() => {
                const hasDeletedForwardedPost = forwardedPosts.length > 0 && forwardedPosts[0] === undefined;
                const hasDeletedForwardedAnswerPost = forwardedAnswerPosts.length > 0 && forwardedAnswerPosts[0] === undefined;
                this.hasOriginalPostBeenDeleted.set(hasDeletedForwardedAnswerPost || hasDeletedForwardedPost);
            });
        });
        // Track posting signal changes (replaces ngOnChanges)
        effect(() => {
            this.posting();
            this.searchConfig();
            this.showChannelReference();
            untracked(() => {
                const posting = this.posting();
                if (!posting) return;
                // Ensure posting is a Post instance; if conversion is needed,
                // return early — the .set() will re-trigger this effect with the converted value.
                if (!(posting instanceof Post)) {
                    this.posting.set(Object.assign(new Post(), posting));
                    return;
                }
                this.contextInformation.set(this.metisService.getContextInformation(posting));
                this.routerLink = this.metisService.getLinkForPost();
                this.queryParams = this.metisService.getQueryParamsForPost(posting);
                this.showAnnouncementIcon.set((getAsChannelDTO(posting.conversation)?.isAnnouncementChannel && this.showChannelReference()) ?? false);
                this.updateShowSearchResultInAnswersHint();
                this.sortAnswerPosts();
            });
        });
    }

    get reactionsBar() {
        return this.reactionsBarComponent();
    }

    onReactionsUpdated(updatedReactions: Reaction[]) {
        const current = this.posting();
        if (!current) {
            return;
        }
        const updated = deepClone(current);
        updated.reactions = updatedReactions;
        this.posting.set(updated);
    }

    /**
     * Returns true if the current post is pinned to the top.
     */
    isPinned(): boolean {
        return this.posting()?.displayPriority === DisplayPriority.PINNED;
    }

    /** Updates internal flag for edit permission */
    onMayEdit(value: boolean) {
        this.mayEdit.set(value);
    }

    /** Updates internal flag for delete permission */
    onMayDelete(value: boolean) {
        this.mayDelete.set(value);
    }

    /** Updates internal flag for pin permission */
    onCanPin(value: boolean) {
        this.canPin.set(value);
    }

    /** Updates internal flag for mark as unread permission */
    onCanMarkAsUnread(value: boolean) {
        this.canMarkAsUnread.set(value);
    }

    /**
     * Handles right-click context menu, sets the active dropdown post,
     * and positions the dropdown near the cursor.
     */
    onRightClick(event: MouseEvent) {
        const targetElement = event.target as HTMLElement;
        const isPointerCursor = window.getComputedStyle(targetElement).cursor === 'pointer';

        if (!isPointerCursor) {
            event.preventDefault();

            if (PostComponent.activeDropdownPost && PostComponent.activeDropdownPost !== this) {
                PostComponent.activeDropdownPost.showDropdown.set(false);
                PostComponent.activeDropdownPost.enableBodyScroll();
            }

            PostComponent.activeDropdownPost = this;

            this.dropdownPosition.set({
                x: event.clientX,
                y: event.clientY,
            });

            this.showDropdown.set(true);
            this.adjustDropdownPosition();
            this.disableBodyScroll();
        }
    }

    /**
     * Adjusts dropdown position if it overflows the screen on the right.
     */
    adjustDropdownPosition() {
        const dropdownWidth = 200;
        const screenWidth = window.innerWidth;

        if (this.dropdownPosition().x + dropdownWidth > screenWidth) {
            this.dropdownPosition.update((position) => ({ ...position, x: screenWidth - dropdownWidth - 10 }));
        }
    }

    /**
     * Disables vertical scroll on main container to avoid background scroll when dropdown is active.
     */
    disableBodyScroll() {
        const mainContainer = this.document.querySelector('.posting-infinite-scroll-container');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow', 'hidden');
        }
    }

    /**
     * Re-enables vertical scroll on the main container.
     */
    enableBodyScroll() {
        const mainContainer = this.document.querySelector('.posting-infinite-scroll-container');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow-y', 'auto');
        }
    }

    /**
     * Closes any open dropdown menus when clicking outside.
     */
    @HostListener('document:click')
    onClickOutside() {
        this.showDropdown.set(false);
        this.enableBodyScroll();
    }

    override ngOnDestroy() {
        super.ngOnDestroy();
        // Clear static reference to prevent memory leaks when component is destroyed
        if (PostComponent.activeDropdownPost === this) {
            PostComponent.activeDropdownPost = undefined;
        }
        // Restore scroll in case the component is destroyed while the dropdown is open
        if (this.showDropdown()) {
            this.enableBodyScroll();
        }
    }

    /**
     * on initialization: evaluates post context and page type
     */
    ngOnInit() {
        super.ngOnInit();
        this.pageType.set(this.metisService.getPageType());
        const posting = this.posting();
        if (posting) {
            this.contextInformation.set(this.metisService.getContextInformation(posting));
        }
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.updateShowSearchResultInAnswersHint();
        this.sortAnswerPosts();
        this.assignPostingToPost();
        this.fetchForwardedMessages();
    }

    updateShowSearchResultInAnswersHint() {
        const posting = this.posting();
        const searchConfig = this.searchConfig();
        if (!searchConfig || !posting?.answers) {
            this.showSearchResultInAnswersHint.set(false);
            return;
        }

        const searchQuery = searchConfig.searchTerm.toLowerCase();
        if (!searchQuery) {
            const selectedAuthorIds = searchConfig.selectedAuthors.map((author) => author.id);
            const isSearchAuthorInAnswers =
                posting.answers?.some((answer) => {
                    const answerAuthorId = answer.author?.id;
                    return selectedAuthorIds.includes(answerAuthorId);
                }) ?? false;
            this.showSearchResultInAnswersHint.set(isSearchAuthorInAnswers);
            return;
        }

        this.showSearchResultInAnswersHint.set(
            posting.answers?.some((answer) => {
                const answerContent = answer.content?.toLowerCase();
                return answerContent?.includes(searchQuery);
            }) ?? false,
        );
    }

    /**
     * Sets originalPostDetails if forwarded post or forwarded answer post exists.
     */
    fetchForwardedMessages(): void {
        try {
            if (this.forwardedPosts().length > 0) {
                this.originalPostDetails.set(this.forwardedPosts()[0]);
            }
            if (this.forwardedAnswerPosts().length > 0) {
                this.originalPostDetails.set(this.forwardedAnswerPosts()[0]);
            }
        } catch (error) {
            throw new Error(error.toString(), { cause: error });
        }
    }

    /**
     * ensures that only when clicking on context without having control key pressed,
     * the modal is dismissed (closed and cleared)
     */
    onNavigateToContext($event: MouseEvent) {
        if (!$event.metaKey) {
            this.modalRef()?.close();
            this.metisConversationService.setActiveConversation(this.contextInformation().queryParams!['conversationId']);
        }
    }

    /**
     * Open create answer modal
     */
    openCreateAnswerPostModal() {
        this.postFooterComponent()?.openCreateAnswerPostModal();
    }

    /**
     * Close create answer modal
     */
    closeCreateAnswerPostModal() {
        this.postFooterComponent()?.closeCreateAnswerPostModal();
    }

    /**
     * sorts answerPosts by two criteria
     * 1. criterion: resolvesPost -> true comes first
     * 2. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
     */
    sortAnswerPosts(): void {
        const posting = this.posting();
        if (!posting?.answers) {
            this.sortedAnswerPosts.set([]);
            return;
        }
        this.sortedAnswerPosts.set(
            [...posting.answers].sort(
                (answerPostA, answerPostB) =>
                    Number(answerPostB.resolvesPost) - Number(answerPostA.resolvesPost) || answerPostA.creationDate!.valueOf() - answerPostB.creationDate!.valueOf(),
            ),
        );
    }

    /**
     * Navigate to the referenced channel
     *
     * @param channelId id of the referenced channel
     */
    onChannelReferenceClicked(channelId: number) {
        const course = this.metisService.getCourse();
        if (isCommunicationEnabled(course)) {
            if (this.isCommunicationPage()) {
                this.metisConversationService.setActiveConversation(channelId);
            } else {
                this.router.navigate(['courses', course.id, 'communication'], {
                    queryParams: {
                        conversationId: channelId,
                    },
                });
            }
        }
    }

    protected setDisplayInlineInput(active: boolean) {
        this.displayInlineInput.set(active);
    }

    private assignPostingToPost() {
        // This is needed because otherwise instanceof returns 'object'.
        const posting = this.posting();
        if (posting && !(posting instanceof Post)) {
            this.posting.set(Object.assign(new Post(), posting));
        }
    }

    /**
     * Emits an event to notify parent components to navigate to the given post.
     */
    protected onTriggerNavigateToPost(post: Posting | undefined) {
        if (!post) {
            return;
        }
        this.onNavigateToPost.emit(post);
    }
}
