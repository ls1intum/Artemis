import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import dayjs from 'dayjs/esm';
import { DisplayPriority } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
})
export class PostingThreadComponent {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode = false;
    @Input() post: Post;
    @Input() showAnswers: boolean;
    @Input() isCommunicationPage: boolean;
    @Input() showChannelReference?: boolean;
    @Input() hasChannelModerationRights = false;
    @Output() openThread = new EventEmitter<Post>();

    protected readonly DisplayPriority = DisplayPriority;
}
