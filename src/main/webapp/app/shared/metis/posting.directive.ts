import { Directive, Input, OnInit } from '@angular/core';

import { Posting } from 'app/entities/metis/posting.model';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() isCourseMessagesPage: boolean;

    @Input() hasChannelModerationRights = false;
    @Input() isThreadSidebar: boolean;

    content?: string;

    ngOnInit(): void {
        this.content = this.posting.content;
    }
}
