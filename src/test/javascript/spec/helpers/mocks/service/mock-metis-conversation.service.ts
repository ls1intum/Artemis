import { Course } from 'app/entities/course.model';
import { BehaviorSubject, EMPTY, Observable, ReplaySubject, Subject } from 'rxjs';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';

export class MockMetisConversationService {
    get course(): Course | undefined {
        return undefined;
    }

    get activeConversation$(): Observable<ConversationDto | undefined> {
        return EMPTY;
    }

    get isServiceSetup$(): Observable<boolean> {
        return new BehaviorSubject(true).asObservable();
    }

    get conversationsOfUser$(): Observable<ConversationDto[]> {
        return new BehaviorSubject([new GroupChatDto()]).asObservable();
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

    setActiveConversation(conversationIdentifier: ConversationDto | number | undefined) {}

    setUpConversationService = (course: Course): Observable<never> => {
        return EMPTY;
    };

    _hasUnreadMessages$: Subject<boolean> = new ReplaySubject<boolean>(1);

    forceRefresh(notifyActiveConversationSubscribers = true, notifyConversationsSubscribers = true): Observable<never> {
        return EMPTY;
    }

    markAsRead(): void {}

    acceptCodeOfConduct(course: Course) {}
}
