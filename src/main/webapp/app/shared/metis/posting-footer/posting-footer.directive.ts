import { Directive, Input } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';

@Directive()
export abstract class PostingFooterDirective<T extends Posting> {
    @Input() posting: T;
    @Input() isThreadSidebar: boolean;
}
