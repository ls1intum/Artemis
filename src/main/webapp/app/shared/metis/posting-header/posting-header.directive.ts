import { Posting } from 'app/entities/metis/posting.model';
import { Directive, EventEmitter, Input, OnInit, Output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';

@Directive()
export abstract class PostingHeaderDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Output() isModalOpen = new EventEmitter<void>();
    isAtLeastTutorInCourse: boolean;
    isAuthorOfPosting: boolean;
    postingIsOfToday: boolean;
    todayFlag: string | undefined;

    protected constructor(protected metisService: MetisService) {}

    /**
     * on initialization: determines if user is at least tutor in the course and if user is author of posting by invoking the metis service,
     * determines if posting is of today and sets the today flag to be shown in the header of the posting
     */
    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.isAuthorOfPosting = this.metisService.metisUserIsAuthorOfPosting(this.posting);
        this.postingIsOfToday = dayjs().isSame(this.posting.creationDate, 'day');
        this.todayFlag = this.getTodayFlag();
    }

    /**
     * sets a flag that replaces the date by "Today" in the posting's header if applicable
     */
    getTodayFlag(): string | undefined {
        if (this.postingIsOfToday) {
            return 'artemisApp.metis.today';
        } else {
            return undefined;
        }
    }

    abstract deletePosting(): void;
}
