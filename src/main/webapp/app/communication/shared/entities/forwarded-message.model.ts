import { BaseEntity } from 'app/shared/model/base-entity';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingType } from 'app/communication/shared/entities/posting.model';

export interface ForwardedMessageDTO {
    id?: number;
    sourceId?: number;
    sourceType?: PostingType;
    destinationPostId?: number;
    destinationAnswerPostId?: number;
    content?: string;
}

export class ForwardedMessage implements BaseEntity {
    public id?: number;
    public sourceId?: number;
    public sourceType?: PostingType;
    public destinationPost?: Post;
    public destinationAnswerPost?: AnswerPost;
    public content?: string;

    private validateDestinations(): boolean {
        const isDestinationPostValid = this.destinationPost !== undefined && this.destinationPost !== null;
        const isDestinationAnswerPostValid = this.destinationAnswerPost !== undefined && this.destinationAnswerPost !== null;
        return (isDestinationPostValid && !isDestinationAnswerPostValid) || (!isDestinationPostValid && isDestinationAnswerPostValid);
    }

    constructor(id?: number, sourceId?: number, sourceType?: PostingType, destinationPost?: Post, destinationAnswerPost?: AnswerPost, content?: string) {
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

    public toDTO(): ForwardedMessageDTO {
        return {
            id: this.id,
            sourceId: this.sourceId,
            sourceType: this.sourceType,
            destinationPostId: this.destinationPost ? this.destinationPost.id : undefined,
            destinationAnswerPostId: this.destinationAnswerPost ? this.destinationAnswerPost.id : undefined,
            content: this.content,
        };
    }
}
