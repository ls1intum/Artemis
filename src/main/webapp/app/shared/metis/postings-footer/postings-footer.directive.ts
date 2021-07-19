import { Directive, Input, OnInit } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import { MetisService } from 'app/shared/metis/metis.service';

@Directive()
export abstract class PostingsFooterDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    isAtLeastTutorInCourse: boolean;

    protected constructor(protected postingService: PostingsService<T>, protected metisService: MetisService) {}

    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
    }
}
