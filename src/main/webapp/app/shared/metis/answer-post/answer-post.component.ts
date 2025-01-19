import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    HostListener,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    Renderer2,
    ViewChild,
    ViewContainerRef,
    inject,
    input,
} from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import dayjs from 'dayjs/esm';
import { animate, style, transition, trigger } from '@angular/animations';
import { Reaction } from 'app/entities/metis/reaction.model';
import { faBookmark, faPencilAlt, faSmile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT, NgClass, NgIf, NgStyle } from '@angular/common';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from '../../language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingHeaderComponent } from '../posting-header/posting-header.component';
import { PostingContentComponent } from '../posting-content/posting-content.components';
import { AnswerPostCreateEditModalComponent } from '../posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { EmojiPickerComponent } from '../emoji/emoji-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

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
        AnswerPostReactionsBarComponent,
        AnswerPostCreateEditModalComponent,
        NgIf,
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

    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();
    isAnswerPost = true;

    @Input() isReadOnlyMode = false;

    // ng-container to render answerPostCreateEditModalComponent
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    @ViewChild(AnswerPostReactionsBarComponent) private reactionsBarComponent!: AnswerPostReactionsBarComponent;

    // Icons
    faBookmark = faBookmark;

    isConsecutive = input<boolean>(false);
    readonly faPencilAlt = faPencilAlt;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    static activeDropdownPost: AnswerPostComponent | null = null;
    mayEdit = false;
    mayDelete = false;

    ngOnInit() {
        super.ngOnInit();
        this.assignPostingToAnswerPost();
    }

    ngOnChanges() {
        this.assignPostingToAnswerPost();
    }

    get reactionsBar() {
        return this.reactionsBarComponent;
    }

    onPostingUpdated(updatedPosting: AnswerPost) {
        this.posting = updatedPosting;
    }

    onReactionsUpdated(updatedReactions: Reaction[]) {
        this.posting = { ...this.posting, reactions: updatedReactions };
    }

    @HostListener('document:click', ['$event'])
    onClickOutside() {
        this.showDropdown = false;
        this.enableBodyScroll();
    }

    private disableBodyScroll() {
        const mainContainer = this.document.querySelector('.thread-answer-post');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow', 'hidden');
        }
    }

    enableBodyScroll() {
        const mainContainer = this.document.querySelector('.thread-answer-post');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow-y', 'auto');
        }
    }

    onMayDelete(value: boolean) {
        this.mayDelete = value;
    }

    onMayEdit(value: boolean) {
        this.mayEdit = value;
    }

    onRightClick(event: MouseEvent) {
        const targetElement = event.target as HTMLElement;
        let isPointerCursor: boolean;
        try {
            isPointerCursor = window.getComputedStyle(targetElement).cursor === 'pointer';
        } catch (error) {
            console.error('Failed to compute style:', error);
            isPointerCursor = true;
        }

        if (!isPointerCursor) {
            event.preventDefault();

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

    adjustDropdownPosition() {
        const dropdownWidth = 200;
        const screenWidth = window.innerWidth;

        if (this.dropdownPosition.x + dropdownWidth > screenWidth) {
            this.dropdownPosition.x = screenWidth - dropdownWidth - 10;
        }
    }

    private static cleanupActiveDropdown(): void {
        if (AnswerPostComponent.activeDropdownPost) {
            AnswerPostComponent.activeDropdownPost.showDropdown = false;
            AnswerPostComponent.activeDropdownPost.enableBodyScroll();
            AnswerPostComponent.activeDropdownPost.changeDetector.detectChanges();
            AnswerPostComponent.activeDropdownPost = null;
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
