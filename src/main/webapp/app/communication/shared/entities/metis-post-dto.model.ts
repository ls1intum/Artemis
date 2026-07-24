import { Post } from 'app/communication/shared/entities/post.model';
import { MetisPostAction } from 'app/communication/metis.util';

export interface MetisPostDTO {
    post: Post;
    action: MetisPostAction;
}
