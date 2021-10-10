import { Component, EventEmitter, OnChanges, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseWideContext } from '../metis.util';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    postIsResolved: boolean;
    readonly CourseWideContext = CourseWideContext;

    constructor(public metisService: MetisService) {
        super();
    }

    ngOnInit() {
        super.ngOnInit();
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }

    ngOnChanges() {
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }
}
