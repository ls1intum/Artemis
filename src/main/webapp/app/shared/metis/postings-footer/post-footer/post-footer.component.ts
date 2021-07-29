import { Component, OnChanges, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Reaction } from 'app/entities/metis/reaction.model';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit, OnChanges {
    tags: string[];

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    /**
     * on initialization: updates the post tags
     */
    ngOnInit(): void {
        this.updateTags();
    }

    /**
     * on changes: updates the post tags
     */
    ngOnChanges(): void {
        this.updateTags();
    }

    buildReaction(emojiData: EmojiData): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiData.id;
        reaction.post = this.posting;
        return reaction;
    }

    /**
     * sets the current post tags, empty error if none exit
     */
    private updateTags(): void {
        if (this.posting.tags) {
            this.tags = this.posting.tags;
        } else {
            this.tags = [];
        }
    }
}
