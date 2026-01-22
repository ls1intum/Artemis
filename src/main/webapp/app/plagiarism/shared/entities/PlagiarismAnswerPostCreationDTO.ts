import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';

export class PlagiarismAnswerPostCreationDTO {
    id?: number;
    content?: string;
    postId!: number;
    resolvesPost?: boolean;
    hasForwardedMessages?: boolean;

    /**
     * Creates a PlagiarismAnswerPostCreationDTO from an AnswerPost entity.
     */
    static of(answerPost: AnswerPost): PlagiarismAnswerPostCreationDTO {
        if (!answerPost.post?.id) {
            throw new Error('AnswerPost must be associated with a Post that has an ID.');
        }

        const dto = new PlagiarismAnswerPostCreationDTO();
        dto.id = answerPost.id;
        dto.content = answerPost.content;
        dto.postId = answerPost.post.id;
        dto.resolvesPost = answerPost.resolvesPost;
        dto.hasForwardedMessages = answerPost.hasForwardedMessages;

        return dto;
    }
}
