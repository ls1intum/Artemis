import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    HostListener,
    Inject,
    Input,
    Output,
    Renderer2,
    ViewChild,
    ViewContainerRef,
    input,
} from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import dayjs from 'dayjs/esm';
import { Posting } from 'app/entities/metis/posting.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { faPencilAlt, faSmile, faThumbtack, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT } from '@angular/common';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();

    @Input()
    isReadOnlyMode = false;
    // ng-container to render answerPostCreateEditModalComponent
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    isConsecutive = input<boolean>(false);
    readonly faPencilAlt = faPencilAlt;
    readonly faSmile = faSmile;
    readonly faTrash = faTrash;
    readonly faThumbtack = faThumbtack;
    static activeDropdownPost: AnswerPostComponent | null = null;
    @ViewChild(AnswerPostReactionsBarComponent) private reactionsBarComponent!: AnswerPostReactionsBarComponent;

    constructor(
        protected changeDetector: ChangeDetectorRef,
        private renderer: Renderer2,
        @Inject(DOCUMENT) private document: Document,
    ) {
        super();
    }

    protected get reactionsBar() {
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
        console.log('disabledasınn');
        const mainContainer = this.document.querySelector('.thread-answer-post');
        console.log(mainContainer);
        if (mainContainer) {
            console.log("main container'ı bulduu");
            this.renderer.setStyle(mainContainer, 'overflow', 'hidden');
        }
    }

    private enableBodyScroll() {
        const mainContainer = this.document.querySelector('.thread-answer-post');
        if (mainContainer) {
            this.renderer.setStyle(mainContainer, 'overflow-y', 'auto');
        }
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
        this.disableBodyScroll();
    }
}
