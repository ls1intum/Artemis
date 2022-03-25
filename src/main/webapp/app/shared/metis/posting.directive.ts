import { Posting } from 'app/entities/metis/posting.model';
import { Directive, EventEmitter, Input, OnInit, Output } from '@angular/core';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Output() isModalOpen = new EventEmitter<void>();
    content?: string;

    ngOnInit(): void {
        this.content = this.posting.content;
    }
}
