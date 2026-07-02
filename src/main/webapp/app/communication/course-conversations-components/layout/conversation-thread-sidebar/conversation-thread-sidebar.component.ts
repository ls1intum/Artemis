import { Component, ElementRef, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import { faArrowLeft, faChevronLeft, faCompress, faExpand, faGripLinesVertical, faXmark } from '@fortawesome/free-solid-svg-icons';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { NgClass } from '@angular/common';
import { PostComponent } from 'app/communication/post/post.component';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { Course } from 'app/course/shared/entities/course.model';
import { ConversationSelectionState } from 'app/communication/shared/course-conversations/course-conversation-selection.state';
import { ResizableConstraints, ResizableDirective } from 'app/shared-ui/directives/resizable.directive';

@Component({
    selector: 'jhi-conversation-thread-sidebar',
    templateUrl: './conversation-thread-sidebar.component.html',
    styleUrls: ['./conversation-thread-sidebar.component.scss'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        PostComponent,
        MessageReplyInlineInputComponent,
        ArtemisTranslatePipe,
        NgClass,
        TutorSuggestionComponent,
        ResizableDirective,
    ],
})
export class ConversationThreadSidebarComponent {
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
                    this.conversation.set(conversation as ConversationDTO);
                    this.hasChannelModerationRights.set(getAsChannelDTO(this.conversation())?.hasChannelModerationRights ?? false);
                }
            });
        });
        effect(() => {
            const activePost = this.activePost();
            untracked(() => {
                if (activePost) {
                    this.post.set(activePost);
                    this.createdAnswerPost.set(this.createEmptyAnswerPost());
                    // After the DOM renders the new post, pin the current pixel width
                    // so that removing or replacing content (e.g. rejecting / editing an
                    // Iris reply) never changes the sidebar width mid-session.
                    setTimeout(() => this.lockWidth(), 0);
                }
            });
        });
    }

    readonly post = signal<Post | undefined>(undefined);
    readonly createdAnswerPost = signal<AnswerPost>(undefined!);
    readonly conversation = signal<ConversationDTO>(undefined!);
    readonly hasChannelModerationRights = signal(false);

    // Icons
    faXmark = faXmark;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    faArrowLeft = faArrowLeft;
    readonly faExpand = faExpand;
    readonly faCompress = faCompress;

    readonly isExpanded = signal(false);

    /**
     * creates empty default answer post that is needed on initialization of a newly opened modal to edit or create an answer post, with accordingly set resolvesPost flag
     * @return AnswerPost created empty default answer post
     */
    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post();
        return answerPost;
    }

    /**
     * Toggles the expanded state of the thread view.
     * If expanded, resets the container width and collapses it;
     * if collapsed, expands the thread view to allow resizing.
     * Also ensures that the tooltip is closed to prevent UI clutter.
     */
    toggleExpand(): void {
        const el = this.threadContainer()?.nativeElement;
        if (el) {
            el.style.width = '';
        }
        this.isExpanded.update((expanded) => !expanded);
        this.expandTooltip()?.close();
        if (!this.isExpanded()) {
            // After collapsing, re-pin the sidebar width once the DOM has settled.
            setTimeout(() => this.lockWidth(), 0);
        }
    }

    /**
     * Emits the close post thread and resets the open post variable
     */
    closeThread() {
        const el = this.threadContainer()?.nativeElement;
        if (el) {
            el.style.width = '';
        }
        this.closePostThread.emit();
        this.conversationSelectionState.setOpenPostId(undefined);
    }

    private lockWidth(): void {
        const el = this.threadContainer()?.nativeElement;
        if (!el || el.style.width) {
            // Already pinned by a previous lock or a user drag — nothing to do.
            return;
        }
        const w = el.getBoundingClientRect().width;
        if (w > 0) {
            // Set an explicit pixel width exactly as interact.js does after a manual resize.
            // This stops flex from shrinking the sidebar when thread content changes
            // (e.g. an Iris reply is rejected or switched into edit mode).
            el.style.width = w + 'px';
        }
    }

    /**
     * Width constraints for the resizable thread section, based on the viewport width when the sidebar opens.
     * Mirrors the previous interact.js restrictSize modifier (min 30% of the window width, max full width). Held in
     * a signal with a stable reference (instead of a getter returning a fresh object every change-detection cycle),
     * which avoided the layout thrash that made the sidebar open jankily.
     */
    readonly resizableConstraints = signal<ResizableConstraints>({ minWidth: window.innerWidth * 0.3, maxWidth: window.innerWidth }).asReadonly();

    scrollEditorIntoView(): void {
        this.scrollBody()?.nativeElement?.scrollTo({
            top: this.scrollBody()?.nativeElement.scrollHeight,
            behavior: 'instant',
        });
    }
}
