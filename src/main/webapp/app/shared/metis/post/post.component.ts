import {
    AfterContentChecked,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    HostListener,
    Inject,
    Input,
    OnChanges,
    OnInit,
    Output,
    Renderer2,
    ViewChild,
    ViewContainerRef,
    input,
} from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ContextInformation, DisplayPriority, PageType, RouteComponents } from '../metis.util';
import { faBookmark, faBullhorn, faCheckSquare, faComments, faPencilAlt, faSmile, faThumbtack, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { OneToOneChatService } from 'app/shared/metis/conversations/one-to-one-chat.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { Router } from '@angular/router';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { animate, style, transition, trigger } from '@angular/animations';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { CdkOverlayOrigin } from '@angular/cdk/overlay';
import { DOCUMENT } from '@angular/common';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss', './../metis.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    animations: [
        trigger('fade', [
            transition(':enter', [style({ opacity: 0 }), animate('300ms ease-in', style({ opacity: 1 }))]),
            transition(':leave', [animate('300ms ease-out', style({ opacity: 0 }))]),
        ]),
    ],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges, AfterContentChecked {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode: boolean;
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post title
    @Input() modalRef?: NgbModalRef;
    @Input() showAnswers: boolean;
    @Output() openThread = new EventEmitter<void>();
    @ViewChild('createAnswerPostModal') createAnswerPostModalComponent: AnswerPostCreateEditModalComponent;
    @ViewChild('createEditModal') createEditModal!: PostCreateEditModalComponent;
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    @ViewChild('postFooter') postFooterComponent: PostFooterComponent;
    showReactionSelector = false;
    @ViewChild('emojiPickerTrigger') emojiPickerTrigger!: CdkOverlayOrigin;
    static activeDropdownPost: PostComponent | null = null;

    displayInlineInput = false;
    routerLink: RouteComponents;
    queryParams = {};
    showAnnouncementIcon = false;
    sortedAnswerPosts: AnswerPost[];
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;

    pageType: PageType;
    contextInformation: ContextInformation;
    readonly PageType = PageType;
    readonly DisplayPriority = DisplayPriority;
    mayEdit: boolean = false;
    mayDelete: boolean = false;
    canPin: boolean = false;

    // Icons
    readonly faBullhorn = faBullhorn;
    readonly faComments = faComments;
    readonly faPencilAlt = faPencilAlt;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    readonly faThumbtack = faThumbtack;
    readonly faCheckSquare = faCheckSquare;
    readonly faBookmark = faBookmark;

    isConsecutive = input<boolean>(false);
    dropdownPosition = { x: 0, y: 0 };
    @ViewChild(PostReactionsBarComponent) protected reactionsBarComponent!: PostReactionsBarComponent;

    constructor(
        public metisService: MetisService,
        public changeDetector: ChangeDetectorRef,
        private oneToOneChatService: OneToOneChatService,
        private metisConversationService: MetisConversationService,
        private router: Router,
        public renderer: Renderer2,
        @Inject(DOCUMENT) private document: Document,
    ) {
        super();
    }

    get reactionsBar() {
        return this.reactionsBarComponent;
    }

    isPinned(): boolean {
        return this.posting.displayPriority === DisplayPriority.PINNED;
    }

    onMayEdit(value: boolean) {
        this.mayEdit = value;
    }

    onMayDelete(value: boolean) {
        this.mayDelete = value;
    }

    onCanPin(value: boolean) {
        this.canPin = value;
    }

    onRightClick(event: MouseEvent) {
        const targetElement = event.target as HTMLElement;
        const isPointerCursor = window.getComputedStyle(targetElement).cursor === 'pointer';

        if (!isPointerCursor) {
            event.preventDefault();

            if (PostComponent.activeDropdownPost && PostComponent.activeDropdownPost !== this) {
                PostComponent.activeDropdownPost.showDropdown = false;
                PostComponent.activeDropdownPost.enableBodyScroll();
                PostComponent.activeDropdownPost.changeDetector.detectChanges();
            }

            PostComponent.activeDropdownPost = this;

            this.dropdownPosition = {
                x: event.clientX,
                y: event.clientY,
            };

            this.showDropdown = true;
            this.adjustDropdownPosition();
            this.disableBodyScroll();
        }
    }

    adjustDropdownPosition() {
        const dropdownWidth = 200;
        const screenWidth = window.innerWidth;

        if (this.dropdownPosition.x + dropdownWidth > screenWidth) {
            this.dropdownPosition.x = screenWidth - dropdownWidth - 10;
        }
    }

    disableBodyScroll() {
        const mainContainer = this.document.querySelector('.posting-infinite-scroll-container');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow', 'hidden');
        }
    }

    enableBodyScroll() {
        const mainContainer = this.document.querySelector('.posting-infinite-scroll-container');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow-y', 'auto');
        }
    }

    @HostListener('document:click', ['$event'])
    onClickOutside() {
        this.showDropdown = false;
        this.enableBodyScroll();
    }

    /**
     * on initialization: evaluates post context and page type
     */
    ngOnInit() {
        super.ngOnInit();
        this.pageType = this.metisService.getPageType();
        this.contextInformation = this.metisService.getContextInformation(this.posting);
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.sortAnswerPosts();
        this.assignPostingToPost();
    }

    /**
     * on changed: re-evaluates context information
     */
    ngOnChanges() {
        this.contextInformation = this.metisService.getContextInformation(this.posting);
        this.routerLink = this.metisService.getLinkForPost();
        this.queryParams = this.metisService.getQueryParamsForPost(this.posting);
        this.showAnnouncementIcon = (getAsChannelDTO(this.posting.conversation)?.isAnnouncementChannel && this.showChannelReference) ?? false;
        this.sortAnswerPosts();
        this.assignPostingToPost();
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when
     * dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.changeDetector.detectChanges();
    }

    /**
     * ensures that only when clicking on context without having control key pressed,
     * the modal is dismissed (closed and cleared)
     */
    onNavigateToContext($event: MouseEvent) {
        if (!$event.metaKey) {
            this.modalRef?.dismiss();
            this.metisConversationService.setActiveConversation(this.contextInformation.queryParams!['conversationId']);
        }
    }

    /**
     * Open create answer modal
     */
    openCreateAnswerPostModal() {
        this.postFooterComponent.openCreateAnswerPostModal();
    }

    /**
     * Close create answer modal
     */
    closeCreateAnswerPostModal() {
        this.postFooterComponent.closeCreateAnswerPostModal();
    }

    /**
     * sorts answerPosts by two criteria
     * 1. criterion: resolvesPost -> true comes first
     * 2. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
     */
    sortAnswerPosts(): void {
        if (!this.posting.answers) {
            this.sortedAnswerPosts = [];
            return;
        }
        this.sortedAnswerPosts = this.posting.answers.sort(
            (answerPostA, answerPostB) =>
                Number(answerPostB.resolvesPost) - Number(answerPostA.resolvesPost) || answerPostA.creationDate!.valueOf() - answerPostB.creationDate!.valueOf(),
        );
    }

    /**
     * Create a or navigate to one-to-one chat with the referenced user
     *
     * @param referencedUserLogin login of the referenced user
     */
    onUserReferenceClicked(referencedUserLogin: string) {
        const course = this.metisService.getCourse();
        if (isMessagingEnabled(course)) {
            if (this.isCommunicationPage) {
                this.metisConversationService.createOneToOneChat(referencedUserLogin).subscribe();
            } else {
                this.oneToOneChatService.create(course.id!, referencedUserLogin).subscribe((res) => {
                    this.router.navigate(['courses', course.id, 'communication'], {
                        queryParams: {
                            conversationId: res.body!.id,
                        },
                    });
                });
            }
        }
    }

    /**
     * Navigate to the referenced channel
     *
     * @param channelId id of the referenced channel
     */
    onChannelReferenceClicked(channelId: number) {
        const course = this.metisService.getCourse();
        if (isCommunicationEnabled(course)) {
            if (this.isCommunicationPage) {
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

    private assignPostingToPost() {
        // This is needed because otherwise instanceof returns 'object'.
        if (this.posting && !(this.posting instanceof Post)) {
            this.posting = Object.assign(new Post(), this.posting);
        }
    }
}
