import { BaseEntity } from 'app/shared/model/base-entity';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

export enum SourceType {
    POST = 'post',
    ANSWER_POST = 'answer_post',
}

export class ForwardedMessage implements BaseEntity {
    public id?: number;
    public sourceId?: number;
    public sourceType?: SourceType;
    public destinationPost?: Post;
    public destinationAnswerPost?: AnswerPost;

    constructor(id?: number, sourceId?: number, sourceType?: SourceType, destinationPost?: Post, destinationAnswerPost?: AnswerPost) {
        this.id = id;
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        this.destinationPost = destinationPost;
        this.destinationAnswerPost = destinationAnswerPost;
    }
}
