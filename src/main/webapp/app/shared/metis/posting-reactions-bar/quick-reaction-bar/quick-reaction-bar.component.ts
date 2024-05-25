import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-quick-reaction-bar',
    templateUrl: './quick-reaction-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
})
export class QuickReactionBarComponent extends PostingsReactionsBarDirective<Post> implements OnInit, OnChanges {
    @Input()
    readOnlyMode = false;

    constructor(metisService: MetisService) {
        super(metisService);
    }

    /**
     * on initialization: -
     */
    ngOnInit() {
        super.ngOnInit();
    }

    /**
     * on changes: -
     */
    ngOnChanges() {
        super.ngOnChanges();
    }

    /**
     * builds and returns a Reaction model out of an emojiId and thereby sets the post property properly
     * @param emojiId emojiId to build the model for
     */
    buildReaction(emojiId: string): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiId;
        reaction.post = this.posting;
        return reaction;
    }
}
