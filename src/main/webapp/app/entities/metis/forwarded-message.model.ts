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
    public destinationPost: Post | undefined;
    public destinationAnswerPost: AnswerPost | undefined;
    public content: string | undefined;

    private validateDestinations(): boolean {
        const isDestinationPostValid = this.destinationPost !== undefined && this.destinationPost !== null;
        const isDestinationAnswerPostValid = this.destinationAnswerPost !== undefined && this.destinationAnswerPost !== null;
        return (isDestinationPostValid && !isDestinationAnswerPostValid) || (!isDestinationPostValid && isDestinationAnswerPostValid);
    }

    constructor(id?: number, sourceId?: number, sourceType?: SourceType, destinationPost?: Post, destinationAnswerPost?: AnswerPost, content?: string) {
        this.id = id;
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        this.destinationPost = destinationPost;
        this.destinationAnswerPost = destinationAnswerPost;
        this.content = content;
        if (!this.validateDestinations()) {
            throw new Error('A ForwardedMessage must have exactly one destination');
        }
    }
}
