import {
    AfterContentChecked,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    HostListener,
    Input,
    OnChanges,
    OnInit,
    Output,
    Renderer2,
    ViewChild,
    ViewContainerRef,
    inject,
    input,
    output,
} from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ContextInformation, DisplayPriority, PageType, RouteComponents } from '../metis.util';
import { faBookmark, faBullhorn, faComments, faPencilAlt, faShare, faSmile, faThumbtack, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { PostingFooterComponent } from 'app/shared/metis/posting-footer/posting-footer.component';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { animate, style, transition, trigger } from '@angular/animations';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { PostingReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { DOCUMENT, NgClass, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from '../../language/translate.directive';
import { PostingHeaderComponent } from '../posting-header/posting-header.component';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PostingContentComponent } from '../posting-content/posting-content.components';
import { MessageInlineInputComponent } from '../message/message-inline-input/message-inline-input.component';
import { EmojiPickerComponent } from '../emoji/emoji-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';
import { Posting } from 'app/entities/metis/posting.model';
import { throwError } from 'rxjs';
import { ForwardedMessageComponent } from 'app/shared/metis/forwarded-message/forwarded-message.component';

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
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges, AfterContentChecked {
    metisService = inject(MetisService);
    changeDetector = inject(ChangeDetectorRef);
    renderer = inject(Renderer2);
    private document = inject<Document>(DOCUMENT);

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
    @ViewChild('postFooter') postFooterComponent: PostingFooterComponent;
    @ViewChild('emojiPickerTrigger') emojiPickerTrigger!: CdkOverlayOrigin;
    @ViewChild(PostingReactionsBarComponent) protected reactionsBarComponent!: PostingReactionsBarComponent<Post>;

    static activeDropdownPost: PostComponent | undefined = undefined;

    showReactionSelector = false;
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
    mayEdit = false;
    mayDelete = false;
    canPin = false;
    originalPostDetails: Post | AnswerPost | undefined = undefined;
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

    isConsecutive = input<boolean>(false);
    forwardedPosts = input<Post[]>([]);
    forwardedAnswerPosts = input<AnswerPost[]>([]);
    dropdownPosition = { x: 0, y: 0 };
    course: Course;

    constructor() {
        super();
        this.course = this.metisService.getCourse() ?? throwError('Course not found');
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
        this.fetchForwardedMessages();
    }

    fetchForwardedMessages(): void {
        if (this.forwardedPosts().length > 0) {
            const forwardedMessage = this.forwardedPosts()[0];

            if (forwardedMessage) {
                this.originalPostDetails = forwardedMessage;
                this.changeDetector.markForCheck();
            }
        }
        if (this.forwardedAnswerPosts().length > 0) {
            const forwardedMessage = this.forwardedAnswerPosts()[0];

            if (forwardedMessage) {
                this.originalPostDetails = forwardedMessage;
                this.changeDetector.markForCheck();
            }
        }
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
        if (isMessagingEnabled(this.course)) {
            if (this.isCommunicationPage) {
                this.metisConversationService.createOneToOneChat(referencedUserLogin).subscribe();
            } else {
                this.oneToOneChatService.create(this.course.id!, referencedUserLogin).subscribe((res) => {
                    this.router.navigate(['courses', this.course.id, 'communication'], {
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

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
