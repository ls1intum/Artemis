import { Course } from 'app/course/shared/entities/course.model';
import { BehaviorSubject, EMPTY, Observable } from 'rxjs';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import dayjs from 'dayjs';
import type { ConversationReadRequest } from 'app/communication/service/course-conversations.service';

export class MockCourseConversationsService {
    get course(): Course | undefined {
        return undefined;
    }

    get activeConversation$(): Observable<ConversationDTO | undefined> {
        return EMPTY;
    }

    get isServiceSetup$(): Observable<boolean> {
        return new BehaviorSubject(true).asObservable();
    }

    get conversationsOfUser$(): Observable<ConversationDTO[]> {
        return new BehaviorSubject([new GroupChatDTO()]).asObservable();
    }

    get isLoading$(): Observable<boolean> {
        return new BehaviorSubject(false).asObservable();
    }

    get isCodeOfConductAccepted$(): Observable<boolean> {
        return new BehaviorSubject(true).asObservable();
    }

    get isCodeOfConductPresented$(): Observable<boolean> {
        return new BehaviorSubject(false).asObservable();
    }

    checkIsCodeOfConductAccepted(): void {}

    setActiveConversation(conversationIdentifier: ConversationDTO | number | undefined) {}

    setConversationViewOpen(isOpen: boolean): void {}

    isConversationMarkedAsUnread(conversationId: number | undefined): boolean {
        return false;
    }

    messagesRequested(conversationId: number): ConversationReadRequest {
        return { unreadRevision: 0 };
    }

    messagesLoaded(conversationId: number, request: ConversationReadRequest): void {}

    setUpConversationService = (course: Course): Observable<never> => {
        return EMPTY;
    };

    createOneToOneChatWithId = (userId: number): Observable<never> => {
        return EMPTY;
    };

    createOneToOneChat = (userId: number): Observable<never> => {
        return EMPTY;
    };

    handleNewMessage = (conversationId: number | undefined, lastMessageDate: dayjs.Dayjs | undefined): void => {};

    forceRefresh(notifyActiveConversationSubscribers = true, notifyConversationsSubscribers = true): Observable<never> {
        return EMPTY;
    }

    acceptCodeOfConduct(course: Course) {}

    checkForUnreadMessages = (course: Course) => {};
}
