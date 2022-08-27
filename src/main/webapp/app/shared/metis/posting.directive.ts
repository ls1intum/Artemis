import { Posting } from 'app/entities/metis/posting.model';
import { Directive, Input, OnInit } from '@angular/core';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() isCourseMessagesPage: boolean;
    content?: string;

    ngOnInit(): void {
        this.content = this.posting.content;
    }
}
