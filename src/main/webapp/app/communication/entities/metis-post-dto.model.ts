import { Post } from 'app/communication/entities/post.model';
import { MetisPostAction } from 'app/communication/metis.util';
import { Notification } from 'app/core/shared/entities/notification.model';

export class MetisPostDTO {
    public post: Post;
    public action: MetisPostAction;
    public notification?: Notification;
}
