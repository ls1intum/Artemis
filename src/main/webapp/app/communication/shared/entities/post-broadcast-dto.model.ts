import { Post } from 'app/communication/shared/entities/post.model';
import { CommunicationCrudAction } from 'app/communication/communication.util';

export interface PostBroadcastDTO {
    post: Post;
    action: CommunicationCrudAction;
}
