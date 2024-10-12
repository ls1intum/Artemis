import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingHeaderDirective } from 'app/shared/metis/posting-header/posting-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { faCheck, faCog, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class AnswerPostHeaderComponent extends PostingHeaderDirective<AnswerPost> implements OnInit, OnChanges {
    @Input()
    isReadOnlyMode = false;

    @Input() lastReadDate?: dayjs.Dayjs;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();

    isAuthorOfOriginalPost: boolean;
    isAnswerOfAnnouncement: boolean;

    // Icons
    faCheck = faCheck;
    faPencilAlt = faPencilAlt;
    faCog = faCog;

    constructor(
        protected metisService: MetisService,
        protected accountService: AccountService,
    ) {
        super(metisService, accountService);
    }

    ngOnInit() {
        super.ngOnInit();
    }

    ngOnChanges() {
        this.setUserProperties();
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * invokes the metis service to delete an answer post
     */
    deletePosting(): void {
        this.metisService.deleteAnswerPost(this.posting);
    }
}
