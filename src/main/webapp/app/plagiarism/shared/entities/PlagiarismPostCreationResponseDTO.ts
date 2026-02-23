import { PlagiarismCaseDTO } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { DisplayPriority, UserRole } from 'app/communication/metis.util';
import { Post } from 'app/communication/shared/entities/post.model';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';

export interface PlagiarismPostCreationResponseDTO {
    id: number;
    content: string;
    title: string;
    visibleForStudents?: boolean;
    creationDate: string;
    author: User;
    authorRole: UserRole;
    resolved: boolean;
    displayPriority?: DisplayPriority;
    plagiarismCase: PlagiarismCaseDTO;
}

export function mapResponseToPost(dto: PlagiarismPostCreationResponseDTO): Post {
    return {
        id: dto.id,
        title: dto.title,
        content: dto.content,
        visibleForStudents: dto.visibleForStudents,
        creationDate: dayjs(dto.creationDate),
        resolved: dto.resolved,
        displayPriority: dto.displayPriority,
        authorRole: dto.authorRole,
        author: dto.author,

        answers: [],
        reactions: [],
        forwardedPosts: [],
        forwardedAnswerPosts: [],

        plagiarismCase: {
            id: dto.plagiarismCase.id,
            verdict: dto.plagiarismCase.verdict,
            student: dto.plagiarismCase.studentId ? ({ id: dto.plagiarismCase.studentId } as User) : undefined,
        } as PlagiarismCase,
    } as Post;
}
