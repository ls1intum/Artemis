import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';

export type PlagiarismAnswerPostCreationDTO = {
    id?: number;
    content: string;
    postId: number;
    resolvesPost?: boolean;
    hasForwardedMessages?: boolean;
};

export function toPlagiarismAnswerPostCreationDTO(answerPost: AnswerPost): PlagiarismAnswerPostCreationDTO {
    if (!answerPost.post?.id) {
        throw new Error('AnswerPost.post.id must be defined');
    }

    return {
        id: answerPost.id,
        content: answerPost.content!,
        postId: answerPost.post.id,
        resolvesPost: answerPost.resolvesPost,
        hasForwardedMessages: answerPost.hasForwardedMessages,
    };
}
