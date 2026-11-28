import { HttpResponse } from '@angular/common/http';
import { IrisActivityItem, IrisActivityKind, IrisActivityState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisChatSession, IrisChatSessionModeEnum } from 'app/openapi/model/iris-chat-session';
import { IrisChatSessionResponse } from 'app/openapi/model/iris-chat-session-response';
import { IrisMessageResponse } from 'app/openapi/model/iris-message-response';
import { IrisPendingContext } from 'app/openapi/model/iris-pending-context';
import { PyrisActivity, PyrisActivityKindEnum, PyrisActivityStateEnum } from 'app/openapi/model/pyris-activity';

const chatModes: Record<IrisChatSessionModeEnum, ChatServiceMode> = {
    PROGRAMMING_EXERCISE_CHAT: ChatServiceMode.PROGRAMMING_EXERCISE,
    TEXT_EXERCISE_CHAT: ChatServiceMode.TEXT_EXERCISE,
    COURSE_CHAT: ChatServiceMode.COURSE,
    LECTURE_CHAT: ChatServiceMode.LECTURE,
};

const activityKinds: Record<PyrisActivityKindEnum, IrisActivityKind> = {
    TOOL: IrisActivityKind.TOOL,
    COMMAND: IrisActivityKind.COMMAND,
};

const activityStates: Record<PyrisActivityStateEnum, IrisActivityState> = {
    RUNNING: IrisActivityState.RUNNING,
    FINISHED: IrisActivityState.FINISHED,
    FAILED: IrisActivityState.FAILED,
};

/** Maps a response body while keeping the server's status, URL and headers. */
export function mapResponseBody<T, U>(response: HttpResponse<T>, mapBody: (body: T) => U): HttpResponse<U> {
    return response.clone({ body: response.body === null ? null : mapBody(response.body) });
}

/** Converts a chat context to the four modes accepted by the Iris REST endpoints. */
export function toPendingContext(context?: { mode: ChatServiceMode; entityId: number }): IrisPendingContext | undefined {
    if (!context) {
        return undefined;
    }
    if (context.mode === ChatServiceMode.TUTOR_SUGGESTION) {
        throw new Error('Tutor suggestion is a session type and cannot be sent as a chat context');
    }
    return { mode: context.mode, entityId: context.entityId };
}

export function mapChatSession(dto: IrisChatSessionResponse): IrisSession {
    return {
        id: dto.id,
        userId: dto.userId,
        mode: dto.mode === undefined ? undefined : chatModes[dto.mode],
        entityId: dto.entityId,
        title: dto.title,
        creationDate: dto.creationDate,
        messages: dto.messages?.map(mapChatMessage),
        latestSuggestions: dto.latestSuggestions,
        citationInfo: dto.citationInfo,
    };
}

export function mapChatSessionSummary(dto: IrisChatSession): IrisSessionDTO {
    return {
        id: dto.id,
        entityId: dto.entityId,
        entityName: dto.entityName,
        title: dto.title,
        creationDate: dto.creationDate,
        lastActivityDate: dto.lastActivityDate,
        mode: chatModes[dto.mode],
    };
}

export function mapChatMessage(dto: IrisMessageResponse): IrisMessageResponseDTO {
    return {
        id: dto.id,
        sentAt: dto.sentAt,
        helpful: dto.helpful,
        sender: dto.sender,
        origin: dto.origin,
        proactiveOutcome: dto.proactiveOutcome,
        proactiveEpisodeId: dto.proactiveEpisodeId,
        // Jackson omits an empty content list.
        content: dto.content ?? [],
        activities: dto.activities?.map(mapActivity),
        messageDifferentiator: dto.messageDifferentiator,
        final: dto.final,
    };
}

function mapActivity(dto: PyrisActivity): IrisActivityItem {
    return {
        id: dto.id,
        kind: activityKinds[dto.kind],
        name: dto.name,
        state: activityStates[dto.state],
        detail: dto.detail,
        result: dto.result,
        durationMillis: dto.durationMillis,
    };
}
