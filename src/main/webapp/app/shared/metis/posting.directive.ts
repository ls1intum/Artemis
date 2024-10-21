import { Posting } from 'app/entities/metis/posting.model';
import { Directive, Input, OnInit } from '@angular/core';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() isCommunicationPage: boolean;
    @Input() showChannelReference?: boolean;

    @Input() hasChannelModerationRights = false;
    @Input() isThreadSidebar: boolean;
    protected abstract get reactionsBar(): any;
    showDropdown = false;
    dropdownPosition = { x: 0, y: 0 };
    showReactionSelector = false;
    clickPosition = { x: 0, y: 0 };

    content?: string;

    ngOnInit(): void {
        this.content = this.posting.content;
    }

    editPosting() {
        console.log('buraya girdii');
        this.reactionsBar.editPosting();
        this.showDropdown = false;
    }

    togglePin() {
        this.reactionsBar.togglePin();
        this.showDropdown = false;
    }

    deletePost() {
        this.reactionsBar.deletePosting();
        this.showDropdown = false;
    }

    selectReaction(event: any) {
        this.reactionsBar.selectReaction(event);
        this.showReactionSelector = false;
    }

    addReaction(event: MouseEvent) {
        event.preventDefault();
        this.showDropdown = false;

        this.clickPosition = {
            x: event.clientX,
            y: event.clientY,
        };

        this.showReactionSelector = true;
    }

    toggleEmojiSelect() {
        this.showReactionSelector = !this.showReactionSelector;
    }
}
