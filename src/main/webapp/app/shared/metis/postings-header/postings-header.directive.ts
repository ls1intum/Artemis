import { Posting } from 'app/entities/metis/posting.model';
import { Directive, Input, OnInit } from '@angular/core';
import * as moment from 'moment';
import { MetisService } from 'app/shared/metis/metis.service';

@Directive()
export abstract class PostingsHeaderDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    isAtLeastTutorInCourse: boolean;
    isAuthorOfPosting: boolean;
    postingIsOfToday: boolean;

    protected constructor(protected metisService: MetisService) {}

    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.isAuthorOfPosting = this.metisService.metisUserIsAuthorOfPosting(this.posting);
        this.postingIsOfToday = moment().isSame(this.posting.creationDate, 'day');
    }

    abstract deletePosting(): void;
}
