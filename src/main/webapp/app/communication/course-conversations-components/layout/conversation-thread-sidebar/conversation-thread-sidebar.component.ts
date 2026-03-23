import { AfterViewInit, Component, ElementRef, OnDestroy, effect, inject, input, output, untracked, viewChild } from '@angular/core';
import interact from 'interactjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { faArrowLeft, faChevronLeft, faCompress, faExpand, faGripLinesVertical, faXmark } from '@fortawesome/free-solid-svg-icons';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgClass } from '@angular/common';
import { PostComponent } from 'app/communication/post/post.component';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationSelectionState } from 'app/communication/shared/course-conversations/course-conversation-selection.state';

@Component({
    selector: 'jhi-conversation-thread-sidebar',
    templateUrl: './conversation-thread-sidebar.component.html',
    styleUrls: ['./conversation-thread-sidebar.component.scss'],
    imports: [FaIconComponent, TranslateDirective, NgbTooltip, PostComponent, MessageReplyInlineInputComponent, ArtemisTranslatePipe, NgClass, TutorSuggestionComponent],
})
export class ConversationThreadSidebarComponent implements AfterViewInit, OnDestroy {
    readonly scrollBody = viewChild<ElementRef<HTMLDivElement>>('scrollBody');
    expandTooltip = viewChild<NgbTooltip>('expandTooltip');
    threadContainer = viewChild<ElementRef>('threadContainer');

    readonly readOnlyMode = input(false);
    readonly activeConversation = input<ConversationDTO | Conversation>();
    readonly activePost = input<Post>();

    course = input<Course>();

    readonly closePostThread = output<void>();
    private readonly conversationSelectionState = inject(ConversationSelectionState);

    constructor() {
        effect(() => {
            const conversation = this.activeConversation();
            untracked(() => {
                if (conversation) {
                    this.conversation = conversation as ConversationDTO;
                    this.hasChannelModerationRights = getAsChannelDTO(this.conversation)?.hasChannelModerationRights ?? false;
                }
            });
        });
        effect(() => {
            const activePost = this.activePost();
            untracked(() => {
                if (activePost) {
                    this.post = activePost;
                    this.createdAnswerPost = this.createEmptyAnswerPost();
                }
            });
        });
    }

    post?: Post;
    createdAnswerPost: AnswerPost;
    conversation: ConversationDTO;
    hasChannelModerationRights = false;

    // Icons
    faXmark = faXmark;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    faArrowLeft = faArrowLeft;
    readonly faExpand = faExpand;
    readonly faCompress = faCompress;

    isExpanded = false;

    /**
     * creates empty default answer post that is needed on initialization of a newly opened modal to edit or create an answer post, with accordingly set resolvesPost flag
     * @return AnswerPost created empty default answer post
     */
    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        return answerPost;
    }

    /**
     * Toggles the expanded state of the thread view.
     * If expanded, resets the container width and collapses it;
     * if collapsed, expands the thread view to allow resizing.
     * Also ensures that the tooltip is closed to prevent UI clutter.
     */
    toggleExpand(): void {
        if (this.threadContainer()) {
            this.threadContainer()!.nativeElement.style.width = '';
        }
        this.isExpanded = !this.isExpanded;
        this.expandTooltip()?.close();
    }

    /**
     * Emits the close post thread and resets the open post variable
     */
    closeThread() {
        this.closePostThread.emit();
        this.conversationSelectionState.setOpenPostId(undefined);
    }

    private interactable: ReturnType<typeof interact> | undefined;

    /**
     * makes message thread section expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        this.interactable = interact('.expanded-thread')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: window.innerWidth * 0.3, height: 0 },
                        max: { width: window.innerWidth, height: 0 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    ngOnDestroy(): void {
        this.interactable?.unset();
    }

    scrollEditorIntoView(): void {
        this.scrollBody()?.nativeElement?.scrollTo({
            top: this.scrollBody()?.nativeElement.scrollHeight,
            behavior: 'instant',
        });
    }
}
