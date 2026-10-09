import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';

/**
 * Request to continue a global search answer in the Iris chat of a course: the chat opens on the chosen lecture or exercise,
 * with the question and the answer the student saw already in it.
 */
export interface IrisGlobalSearchHandoffDTO {
    courseId: number;
    /** The lecture or exercise the chat starts on; absent for the course itself. */
    context?: { mode: ChatServiceMode; entityId: number };
    question: string;
    /** The answer with its citations already in the chat's citation format. */
    answer: string;
}
