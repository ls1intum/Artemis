import {
    AfterContentChecked,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    HostListener,
    OnChanges,
    OnInit,
    Renderer2,
    inject,
    input,
    model,
    output,
    viewChild,
} from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ContextInformation, DisplayPriority, PageType, RouteComponents } from '../metis.util';
import { faBookmark, faBullhorn, faComments, faPencilAlt, faSmile, faThumbtack, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { PostingFooterComponent } from 'app/shared/metis/posting-footer/posting-footer.component';
import { isCommunicationEnabled } from 'app/entities/course.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { animate, style, transition, trigger } from '@angular/animations';
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
import { PostingReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';

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
    ],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges, AfterContentChecked {
    metisService = inject(MetisService);
    changeDetector = inject(ChangeDetectorRef);
    renderer = inject(Renderer2);
    private document = inject<Document>(DOCUMENT);

    lastReadDate = input<dayjs.Dayjs | undefined>(undefined);
    readOnlyMode = input<boolean>(false);
    previewMode = input<boolean>(false);
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post title
    modalRef = input<NgbModalRef | undefined>(undefined);
    showAnswers = model<boolean>(false);

    openThread = output<void>();

    postFooterComponent = viewChild<PostingFooterComponent>('postFooter');
    reactionsBarComponent = viewChild.required<PostingReactionsBarComponent<Post>>(PostingReactionsBarComponent);

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

    // Icons
    readonly faBullhorn = faBullhorn;
    readonly faComments = faComments;
    readonly faPencilAlt = faPencilAlt;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    readonly faThumbtack = faThumbtack;
    readonly faBookmark = faBookmark;

    isConsecutive = input<boolean>(false);
    dropdownPosition = { x: 0, y: 0 };

    get reactionsBar() {
        return this.reactionsBarComponent();
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
            this.modalRef()?.dismiss();
            this.metisConversationService.setActiveConversation(this.contextInformation.queryParams!['conversationId']);
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
