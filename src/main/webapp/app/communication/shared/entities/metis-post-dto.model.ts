import { Post } from 'app/communication/shared/entities/post.model';
import { MetisPostAction } from 'app/communication/metis.util';

export class MetisPostDTO {
    public post: Post;
    public action: MetisPostAction;
}
