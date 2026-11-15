import { CommentContent, CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { Comment, CommentType, CreateComment } from 'app/exercise/shared/entities/review/comment.model';
import { CommentThread, CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment as ApiComment, CommentTypeEnum } from 'app/openapi/model/comment';
import { CommentContent as ApiCommentContent } from 'app/openapi/model/comment-content';
import { CommentThread as ApiCommentThread, CommentThreadTargetTypeEnum } from 'app/openapi/model/comment-thread';
import { CreateCommentThread as ApiCreateCommentThread } from 'app/openapi/model/create-comment-thread';
import { UserCommentContent } from 'app/openapi/model/user-comment-content';

export function toUserComment({ text }: CreateComment): UserCommentContent {
    return { contentType: 'USER', text };
}

export function toCommentThread(thread: CreateCommentThread): ApiCreateCommentThread {
    return {
        targetType: thread.targetType,
        auxiliaryRepositoryId: thread.auxiliaryRepositoryId,
        initialFilePath: thread.initialFilePath,
        initialLineNumber: thread.initialLineNumber,
        initialComment: toUserComment(thread.initialComment),
    };
}

export function fromCommentThread(dto: ApiCommentThread): CommentThread {
    return {
        id: dto.id,
        groupId: dto.groupId,
        exerciseId: dto.exerciseId,
        targetType: fromThreadLocation(dto.targetType),
        auxiliaryRepositoryId: dto.auxiliaryRepositoryId,
        initialVersionId: dto.initialVersionId,
        initialCommitSha: dto.initialCommitSha,
        filePath: dto.filePath,
        initialFilePath: dto.initialFilePath,
        lineNumber: dto.lineNumber,
        initialLineNumber: dto.initialLineNumber,
        outdated: dto.outdated,
        resolved: dto.resolved,
        comments: dto.comments?.map(fromComment) ?? [],
    };
}

export function fromComment(dto: ApiComment): Comment {
    return {
        id: dto.id,
        threadId: dto.threadId,
        authorName: dto.authorName,
        type: fromCommentType(dto.type),
        content: fromCommentContent(dto.content),
        initialVersionId: dto.initialVersionId,
        initialCommitSha: dto.initialCommitSha,
        createdDate: dto.createdDate,
        lastModifiedDate: dto.lastModifiedDate,
    };
}

function fromThreadLocation(type: CommentThreadTargetTypeEnum): CommentThreadLocationType {
    switch (type) {
        case 'PROBLEM_STATEMENT':
            return CommentThreadLocationType.PROBLEM_STATEMENT;
        case 'TEMPLATE_REPO':
            return CommentThreadLocationType.TEMPLATE_REPO;
        case 'SOLUTION_REPO':
            return CommentThreadLocationType.SOLUTION_REPO;
        case 'TEST_REPO':
            return CommentThreadLocationType.TEST_REPO;
        case 'AUXILIARY_REPO':
            return CommentThreadLocationType.AUXILIARY_REPO;
    }
}

function fromCommentType(type: CommentTypeEnum): CommentType {
    switch (type) {
        case 'USER':
            return CommentType.USER;
        case 'CONSISTENCY_CHECK':
            return CommentType.CONSISTENCY_CHECK;
    }
}

function fromCommentContent(dto: ApiCommentContent): CommentContent {
    switch (dto.contentType) {
        case 'USER':
            return { contentType: CommentContentType.USER, text: dto.text };
        case 'CONSISTENCY_CHECK':
            return {
                contentType: CommentContentType.CONSISTENCY_CHECK,
                severity: dto.severity,
                category: dto.category,
                text: dto.text,
                suggestedFix: dto.suggestedFix,
            };
    }
}
