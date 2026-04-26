import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    HostListener,
    OnDestroy,
    OnInit,
    Renderer2,
    ViewContainerRef,
    effect,
    inject,
    input,
    output,
    untracked,
    viewChild,
} from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingDirective } from 'app/communication/directive/posting.directive';
import dayjs from 'dayjs/esm';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { faBookmark, faCheck, faPencilAlt, faShare, faSmile, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT, NgClass, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingHeaderComponent } from '../posting-header/posting-header.component';
import { AnswerPostCreateEditModalComponent } from '../posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { EmojiPickerComponent } from '../emoji/emoji-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { captureException } from '@sentry/angular';
import { deepClone } from 'app/shared/util/deep-clone.util';
import { PostingReactionsBarComponent } from 'app/communication/posting-reactions-bar/posting-reactions-bar.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        NgClass,
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        PostingHeaderComponent,
        PostingContentComponent,
        PostingReactionsBarComponent,
        AnswerPostCreateEditModalComponent,
        NgStyle,
        CdkOverlayOrigin,
        CdkConnectedOverlay,
        EmojiPickerComponent,
        ArtemisDatePipe,
    ],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> implements OnInit, OnDestroy {
    changeDetector = inject(ChangeDetectorRef);
    renderer = inject(Renderer2);
    private document = inject<Document>(DOCUMENT);

    lastReadDate = input<dayjs.Dayjs | undefined>(undefined);
    isLastAnswer = input<boolean>(false);
    isReadOnlyMode = input<boolean>(false);
    isConsecutive = input<boolean>(false);

    openPostingCreateEditModal = output<void>();
    userReferenceClicked = output<string>();
    channelReferenceClicked = output<number>();

    containerRef = viewChild.required('createEditAnswerPostContainer', { read: ViewContainerRef });
    reactionsBarComponent = viewChild<PostingReactionsBarComponent<AnswerPost>>(PostingReactionsBarComponent);

    isAnswerPost = true;
    course: Course;

    // Icons
    faBookmark = faBookmark;

    readonly faPencilAlt = faPencilAlt;
    readonly faShare = faShare;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    readonly faCheck = faCheck;
    readonly faTriangleExclamation = faTriangleExclamation;
    static activeDropdownPost: AnswerPostComponent | undefined = undefined;
    mayEdit = false;
    mayDelete = false;
    isVerifying = false;
    isEditingIrisReply = false;
    editedIrisContent = '';

    constructor() {
        super();
        this.course = this.metisService.getCourse();
        // Track posting signal changes (replaces ngOnChanges)
        effect(() => {
            this.posting();
            untracked(() => {
                const posting = this.posting();
                if (!posting) return;
                if (!(posting instanceof AnswerPost)) {
                    this.posting.set(Object.assign(new AnswerPost(), posting));
                }
            });
        });
    }

    ngOnInit() {
        super.ngOnInit();
        this.assignPostingToAnswerPost();
    }

    get reactionsBar() {
        return this.reactionsBarComponent();
    }

    onPostingUpdated(updatedPosting: AnswerPost) {
        this.posting.set(updatedPosting);
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
     * Closes dropdown if user clicks anywhere outside the component.
     */
    @HostListener('document:click')
    onClickOutside() {
        this.showDropdown = false;
        this.enableBodyScroll();
    }

    /**
     * Disables vertical scrolling in the thread answer post container.
     * Prevents background scroll when context menu is open.
     */
    private disableBodyScroll() {
        const mainContainer = this.document.querySelector('.thread-answer-post');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow', 'hidden');
        }
    }

    /**
     * Re-enables vertical scrolling in the thread container.
     */
    enableBodyScroll() {
        const mainContainer = this.document.querySelector('.thread-answer-post');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow-y', 'auto');
        }
    }

    /** Updates internal flag for delete permission */
    onMayDelete(value: boolean) {
        this.mayDelete = value;
    }

    /** Updates internal flag for edit permission */
    onMayEdit(value: boolean) {
        this.mayEdit = value;
    }

    /** True when the current answer is an Iris-generated reply that has not yet been verified by a tutor. */
    get isUnverifiedIris(): boolean {
        const posting = this.posting();
        return !!posting && posting.author?.bot === true && posting.verified === false;
    }

    /** True for users who are allowed to approve, edit, or reject unverified Iris replies. */
    get mayVerify(): boolean {
        return this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    /**
     * Approves the current Iris answer, optionally replacing its content. The websocket update
     * broadcast by the server will refresh the cached post and remove the badge from the UI.
     */
    approveAnswer(content?: string): void {
        const posting = this.posting();
        if (!posting?.id || this.isVerifying) {
            return;
        }
        this.isVerifying = true;
        this.metisService.verifyAnswerPost(posting, content?.trim() || undefined).subscribe({
            next: (verified) => {
                this.posting.set(Object.assign(new AnswerPost(), verified));
                this.isEditingIrisReply = false;
                this.isVerifying = false;
                this.changeDetector.detectChanges();
            },
            error: () => {
                this.isVerifying = false;
                this.changeDetector.detectChanges();
            },
        });
    }

    /** Switches to inline-edit mode so the tutor can adjust the Iris content before approving. */
    editAnswer(): void {
        this.editedIrisContent = this.posting()?.content ?? '';
        this.isEditingIrisReply = true;
    }

    /** Cancels inline editing without making any changes. */
    cancelEditAnswer(): void {
        this.isEditingIrisReply = false;
        this.editedIrisContent = '';
    }

    /** Rejects an unverified Iris answer by deleting it directly (no undo timer). */
    rejectAnswer(): void {
        const posting = this.posting();
        if (!posting?.id || this.isVerifying) {
            return;
        }
        this.isVerifying = true;
        this.metisService.deleteAnswerPost(posting);
        this.isVerifying = false;
    }

    /**
     * Displays custom context menu on right-click,
     * and sets this component as the currently active dropdown.
     */
    onRightClick(event: MouseEvent) {
        const targetElement = event.target as HTMLElement;
        let isPointerCursor: boolean;
        try {
            isPointerCursor = window.getComputedStyle(targetElement).cursor === 'pointer';
        } catch (error) {
            captureException(error);
            isPointerCursor = true;
        }

        if (!isPointerCursor) {
            event.preventDefault();

            // Close any other active dropdown
            if (AnswerPostComponent.activeDropdownPost !== this) {
                AnswerPostComponent.cleanupActiveDropdown();
            }

            AnswerPostComponent.activeDropdownPost = this;

            this.dropdownPosition = {
                x: event.clientX,
                y: event.clientY,
            };

            this.showDropdown = true;
            this.adjustDropdownPosition();
            this.disableBodyScroll();
        }
    }

    /**
     * Adjusts dropdown position if it would overflow the screen.
     */
    adjustDropdownPosition() {
        const dropdownWidth = 200;
        const screenWidth = window.innerWidth;

        if (this.dropdownPosition.x + dropdownWidth > screenWidth) {
            this.dropdownPosition.x = screenWidth - dropdownWidth - 10;
        }
    }

    /**
     * Static utility that clears the currently active dropdown post,
     * re-enables scrolling and updates view.
     */
    private static cleanupActiveDropdown(): void {
        if (AnswerPostComponent.activeDropdownPost) {
            AnswerPostComponent.activeDropdownPost.showDropdown = false;
            AnswerPostComponent.activeDropdownPost.enableBodyScroll();
            AnswerPostComponent.activeDropdownPost.changeDetector.detectChanges();
            AnswerPostComponent.activeDropdownPost = undefined;
        }
    }

    ngOnDestroy(): void {
        if (AnswerPostComponent.activeDropdownPost === this) {
            AnswerPostComponent.cleanupActiveDropdown();
        }
    }

    private assignPostingToAnswerPost() {
        // This is needed because otherwise instanceof returns 'object'.
        const posting = this.posting();
        if (posting && !(posting instanceof AnswerPost)) {
            this.posting.set(Object.assign(new AnswerPost(), posting));
        }
    }
}
