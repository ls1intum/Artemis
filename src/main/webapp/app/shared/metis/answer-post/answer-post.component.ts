import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    HostListener,
    Inject,
    Input,
    OnChanges,
    OnDestroy,
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
import { faBookmark, faPencilAlt, faShare, faSmile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT } from '@angular/common';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { Course } from 'app/entities/course.model';

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
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> implements OnInit, OnChanges, OnDestroy {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();
    isAnswerPost = true;
    course: Course;

    @Input()
    isReadOnlyMode = false;
    // ng-container to render answerPostCreateEditModalComponent

    // Icons
    faBookmark = faBookmark;

    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    isConsecutive = input<boolean>(false);
    readonly faPencilAlt = faPencilAlt;
    readonly faShare = faShare;
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
        this.course = this.metisService.getCourse();
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
        const targetElement = event.target as HTMLElement;
        let isPointerCursor = false;
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
