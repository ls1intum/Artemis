import { Post } from 'app/entities/metis/post.model';
import { MetisPostAction } from 'app/shared/metis/metis.util';
import { Notification } from 'app/entities/notification.model';

export class MetisPostDTO {
    public post: Post;
    public action: MetisPostAction;
    public notification?: Notification;
}
