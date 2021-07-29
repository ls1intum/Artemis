import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Reaction } from 'app/entities/metis/reaction.model';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['./answer-post-footer.component.scss'],
})
export class AnswerPostFooterComponent extends PostingsFooterDirective<AnswerPost> implements OnInit {
    @Output() toggleApproveChange: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();
    isAtLeastTutorInCourse: boolean;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    /**
     * on initialization: invoke the metis service to determine if current user is at least tutor in course
     */
    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    buildReaction(emojiData: EmojiData): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiData.id;
        reaction.answerPost = this.posting;
        return reaction;
    }

    /**
     * toggles the tutorApproved property of an answer post if the user is at least tutor in a course,
     * delegates the update to the metis service
     */
    toggleApprove(): void {
        if (this.isAtLeastTutorInCourse) {
            this.posting.tutorApproved = !this.posting.tutorApproved;
            this.metisService.updateAnswerPost(this.posting).subscribe(() => {});
        }
    }
}
