import { Directive, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import { Reaction } from 'app/entities/metis/reaction.model';

@Directive()
export abstract class PostingsFooterDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() isAtLeastTutorInCourse: boolean;
    @Input() courseId: number;
    @Output() onApprove: EventEmitter<T> = new EventEmitter<T>();
    @Output() onReaction: EventEmitter<Reaction> = new EventEmitter<Reaction>();

    protected constructor(protected postingService: PostingsService<T>) {}

    ngOnInit(): void {}
}
