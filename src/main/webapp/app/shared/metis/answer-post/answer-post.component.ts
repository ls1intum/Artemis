import {
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
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import dayjs from 'dayjs/esm';
import { animate, style, transition, trigger } from '@angular/animations';
import { Posting } from 'app/entities/metis/posting.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { faBookmark, faPencilAlt, faSmile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT, NgClass, NgIf, NgStyle } from '@angular/common';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { AnswerPostCreateEditModalComponent } from '../posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { AnswerPostHeaderComponent } from 'app/shared/metis/posting-header/answer-post-header/answer-post-header.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';

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
    standalone: true,
    imports: [
        NgClass,
        FaIconComponent,
        TranslateDirective,
        AnswerPostHeaderComponent,
        PostingContentComponent,
        AnswerPostReactionsBarComponent,
        AnswerPostCreateEditModalComponent,
        NgIf,
        NgStyle,
        CdkOverlayOrigin,
        CdkConnectedOverlay,
        EmojiPickerComponent,
    ],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> implements OnInit, OnChanges {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();
    isAnswerPost = true;

    @Input()
    isReadOnlyMode = false;
    // ng-container to render answerPostCreateEditModalComponent

    // Icons
    faBookmark = faBookmark;

    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    isConsecutive = input<boolean>(false);
    readonly faPencilAlt = faPencilAlt;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    static activeDropdownPost: AnswerPostComponent | null = null;
    mayEdit: boolean = false;
    mayDelete: boolean = false;
    @ViewChild(AnswerPostReactionsBarComponent) private reactionsBarComponent!: AnswerPostReactionsBarComponent;

    constructor(
        public changeDetector: ChangeDetectorRef,
        public renderer: Renderer2,
        @Inject(DOCUMENT) private document: Document,
    ) {
        super();
    }

    ngOnInit() {
        super.ngOnInit();
        this.assignPostingToAnswerPost();
    }

    ngOnChanges(): void {
        this.assignPostingToAnswerPost();
    }

    get reactionsBar() {
        return this.reactionsBarComponent;
    }

    onPostingUpdated(updatedPosting: Posting) {
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
        event.preventDefault();

        if (AnswerPostComponent.activeDropdownPost && AnswerPostComponent.activeDropdownPost !== this) {
            AnswerPostComponent.activeDropdownPost.showDropdown = false;
            AnswerPostComponent.activeDropdownPost.enableBodyScroll();
            AnswerPostComponent.activeDropdownPost.changeDetector.detectChanges();
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

    adjustDropdownPosition() {
        const dropdownWidth = 200;
        const screenWidth = window.innerWidth;

        if (this.dropdownPosition.x + dropdownWidth > screenWidth) {
            this.dropdownPosition.x = screenWidth - dropdownWidth - 10;
        }
    }

    private assignPostingToAnswerPost() {
        // This is needed because otherwise instanceof returns 'object'.
        if (this.posting && !(this.posting instanceof AnswerPost)) {
            this.posting = Object.assign(new AnswerPost(), this.posting);
        }
    }
}
