import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { faSmile } from '@fortawesome/free-regular-svg-icons';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-answer-post-reactions-bar',
    templateUrl: './answer-post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
})
export class AnswerPostReactionsBarComponent extends PostingsReactionsBarDirective<AnswerPost> implements OnInit, OnChanges {
    @Input() isLastAnswer = false;
    // Icons
    farSmile = faSmile;

    constructor(metisService: MetisService) {
        super(metisService);
    }

    /**
     * builds and returns a Reaction model out of an emojiId and thereby sets the answerPost property properly
     * @param emojiId emojiId to build the model for
     */
    buildReaction(emojiId: string): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiId;
        reaction.answerPost = this.posting;
        return reaction;
    }
}
