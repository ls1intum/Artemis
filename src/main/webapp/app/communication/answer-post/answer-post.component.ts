import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    HostListener,
    OnChanges,
    OnDestroy,
    OnInit,
    Renderer2,
    ViewContainerRef,
    inject,
    input,
    output,
    viewChild,
} from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingDirective } from 'app/communication/directive/posting.directive';
import dayjs from 'dayjs/esm';
import { animate, style, transition, trigger } from '@angular/animations';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { faBookmark, faPencilAlt, faShare, faSmile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT, NgClass, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingHeaderComponent } from '../posting-header/posting-header.component';
import { AnswerPostCreateEditModalComponent } from '../posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { EmojiPickerComponent } from '../emoji/emoji-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { captureException } from '@sentry/angular';
import { PostingReactionsBarComponent } from 'app/communication/posting-reactions-bar/posting-reactions-bar.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.component.scss'],
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
export class AnswerPostComponent extends PostingDirective<AnswerPost> implements OnInit, OnChanges, OnDestroy {
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
    static activeDropdownPost: AnswerPostComponent | undefined = undefined;
    mayEdit = false;
    mayDelete = false;

    constructor() {
        super();
        this.course = this.metisService.getCourse();
    }

    ngOnInit() {
        super.ngOnInit();
        this.assignPostingToAnswerPost();
    }

    ngOnChanges() {
        this.assignPostingToAnswerPost();
    }

    get reactionsBar() {
        return this.reactionsBarComponent();
    }

    onPostingUpdated(updatedPosting: AnswerPost) {
        this.posting = updatedPosting;
    }

    onReactionsUpdated(updatedReactions: Reaction[]) {
        this.posting = Object.assign({}, this.posting, { reactions: updatedReactions });
    }

    /**
     * Closes dropdown if user clicks anywhere outside the component.
     */
    @HostListener('document:click', ['$event'])
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
        if (this.posting && !(this.posting instanceof AnswerPost)) {
            this.posting = Object.assign(new AnswerPost(), this.posting);
        }
    }
}
