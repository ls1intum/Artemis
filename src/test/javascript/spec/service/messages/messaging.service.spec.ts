import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Course } from 'app/entities/course.model';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockProvider } from 'ng-mocks';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import {
    conversationBetweenUser1Tutor,
    conversationBetweenUser1User2,
    conversationCreatedDTO,
    conversationReadDTO,
    conversationToCreateUser1,
    conversationUser1,
    metisCourse,
} from '../../helpers/sample/metis-sample-data';
import { MessagingService } from 'app/shared/metis/messaging.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { MockConversationService } from '../../helpers/mocks/service/mock-conversation.service';
import { MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';
import { last, of } from 'rxjs';

describe('Messaging Service', () => {
    let messagingService: MessagingService;
    let conversationService: ConversationService;

    let websocketService: JhiWebsocketService;
    let websocketServiceSubscribeSpy: jest.SpyInstance;
    let websocketServiceReceiveStub: jest.SpyInstance;

    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MessagingService,
                MockProvider(SessionStorageService),
                { provide: ConversationService, useClass: MockConversationService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        });
        messagingService = TestBed.inject(MessagingService);
        conversationService = TestBed.inject(ConversationService);
        websocketService = TestBed.inject(JhiWebsocketService);

        course = metisCourse;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('Invoke conversation service methods', () => {
        beforeEach(() => {
            websocketServiceReceiveStub = jest.spyOn(websocketService, 'receive');
            websocketServiceSubscribeSpy = jest.spyOn(websocketService, 'subscribe');
        });

        it('should load conversations of user create a conversation', fakeAsync(() => {
            const conversationServiceSpyGetConversationsOfUserStub = jest.spyOn(conversationService, 'getConversationsOfUser');
            messagingService.getConversationsOfUser(course.id!);
            expect(conversationServiceSpyGetConversationsOfUserStub).toHaveBeenCalledOnce();
        }));

        it('should create new conversation', fakeAsync(() => {
            messagingService.getConversationsOfUser(course.id!);

            tick();

            const conversationServiceSpyCreateConversationStub = jest.spyOn(conversationService, 'create');
            const createdConversationStub = messagingService.createConversation(course.id!, conversationToCreateUser1).subscribe((createdConversation) => {
                expect(createdConversation).toEqual(conversationUser1);
            });
            expect(conversationServiceSpyCreateConversationStub).toHaveBeenCalledOnce();

            tick();
            createdConversationStub.unsubscribe();
        }));

        it('should add new conversation to cache arriving via websocket subscription', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of(conversationCreatedDTO));
            messagingService.getConversationsOfUser(course.id!);

            tick();

            // subscribe to websocket topic
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledWith(`/user${MetisWebsocketChannelPrefix}courses/${course.id}/conversations/user/${messagingService.userId}`);

            tick();

            messagingService.conversations.subscribe((conversations) => {
                // arriving new conversation should be displayed on top
                expect(conversations[0]).toBe(conversationCreatedDTO.conversation);
                expect(conversations).toHaveLength(3);
            });

            tick();
        }));

        it('should update read conversation arriving via websocket subscription', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of(conversationReadDTO));
            messagingService.getConversationsOfUser(course.id!);

            tick();

            messagingService.conversations.pipe(last()).subscribe((conversations) => {
                expect(conversations[0]).toBe(conversationBetweenUser1User2);
                // arriving read conversation should not be displayed on top
                expect(conversations[1]).toBe(conversationBetweenUser1Tutor);
                expect(conversations).toHaveLength(2);
            });

            tick();
        }));
    });
});
