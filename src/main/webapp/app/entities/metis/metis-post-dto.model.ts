import { Post } from 'app/entities/metis/post.model';
import { MetisPostAction } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { Notification } from 'app/entities/notification.model';

export class MetisPostDTO {
    public post: Post;
    public action: MetisPostAction;
    public conversation?: Conversation;
    public notification?: Notification;
}
