import { Post } from 'app/entities/metis/post.model';
import { MetisPostAction } from 'app/shared/metis/metis.util';

export class MetisPostDTO {
    public post: Post;
    public action: MetisPostAction;
}
