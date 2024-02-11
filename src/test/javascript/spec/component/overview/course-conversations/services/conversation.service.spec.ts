import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { ConversationMemberSearchFilter, ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../helpers/conversationExampleModels';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../../helpers/mocks/service/mock-notification.service';

describe('ConversationService', () => {
    let service: ConversationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: NotificationService, useClass: MockNotificationService },
            ],
        });
        service = TestBed.inject(ConversationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('searchMembersOfConversation', fakeAsync(() => {
        const returnedFromService = [new ConversationUserDTO()];
        const expected = returnedFromService;

        service
            .searchMembersOfConversation(1, 1, 'test', 1, 1, ConversationMemberSearchFilter.ALL)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toEqual(expected));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getConversationsOfUser', fakeAsync(() => {
        const returnedFromService = [new ConversationUserDTO()];
        const expected = returnedFromService;

        service
            .getConversationsOfUser(1)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toEqual(expected));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('updateIsFavorite', fakeAsync(() => {
        service
            .updateIsFavorite(1, 1, true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('updateIsHidden', fakeAsync(() => {
        service
            .updateIsHidden(1, 1, false)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('updateIsMuted', fakeAsync(() => {
        service
            .updateIsMuted(1, 1, false)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('markAsRead', () => {
        service
            .markAsRead(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'PATCH' });
        req.flush({});
    });

    it('acceptCodeOfConduct', () => {
        service
            .acceptCodeOfConduct(1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'PATCH' });
        req.flush({});
    });

    it('checkIsCodeOfConductAccepted', () => {
        service
            .checkIsCodeOfConductAccepted(1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeTrue());
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(true);
    });

    it('getResponsibleUsersForCodeOfConduct', () => {
        service
            .getResponsibleUsersForCodeOfConduct(1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeEmpty());
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([]);
    });

    it('should return the correct conversation name', () => {
        const requestingUser = { id: 1, login: 'test', isRequestingUser: true } as ConversationUserDTO;
        const otherUser = { isRequestingUser: false, firstName: 'timo', lastName: 'moritz', login: 'login' } as ConversationUserDTO;
        const otherUserTwo = { isRequestingUser: false, firstName: 'albert', lastName: 'einstein', login: 'login2' } as ConversationUserDTO;
        const otherUserThree = { isRequestingUser: false, firstName: 'peter', lastName: 'max', login: 'login3' } as ConversationUserDTO;

        // undefined
        expect(service.getConversationName(undefined)).toBe('');
        // channel
        const channel = generateExampleChannelDTO({});
        expect(service.getConversationName(channel)).toBe(channel.name);
        // one to one chat
        const oneToOneChat = generateOneToOneChatDTO({});
        oneToOneChat.members = [requestingUser, otherUser];
        expect(service.getConversationName(oneToOneChat)).toBe('timo moritz');
        expect(service.getConversationName(oneToOneChat, true)).toBe('timo moritz (login)');
        // group chat with explicit name
        const groupChat = generateExampleGroupChatDTO({ name: 'hello' });
        expect(service.getConversationName(groupChat)).toBe(groupChat.name);
        // group chat without any other user
        const groupChatWithoutOtherUser = generateExampleGroupChatDTO({ name: '' });
        groupChatWithoutOtherUser.members = [requestingUser];
        expect(service.getConversationName(groupChatWithoutOtherUser)).toBe('artemisApp.conversationsLayout.onlyYou');
        // group chat without explicit name and one other
        const groupChatWithOneOther = generateExampleGroupChatDTO({ name: '' });
        groupChatWithOneOther.members = [requestingUser, otherUser];
        expect(service.getConversationName(groupChatWithOneOther)).toBe('timo moritz');
        expect(service.getConversationName(groupChatWithOneOther, true)).toBe('timo moritz (login)');
        // group chat without explicit name and two others
        const groupChatWithTwoOthers = generateExampleGroupChatDTO({ name: '' });
        groupChatWithTwoOthers.members = [requestingUser, otherUser, otherUserTwo];
        expect(service.getConversationName(groupChatWithTwoOthers)).toBe('timo moritz, albert einstein');
        // group chat without explicit name and three others
        const groupChatWithThreeOthers = generateExampleGroupChatDTO({ name: '' });
        groupChatWithThreeOthers.members = [requestingUser, otherUser, otherUserTwo, otherUserThree];
        expect(service.getConversationName(groupChatWithThreeOthers)).toBe('timo moritz, albert einstein, artemisApp.conversationsLayout.others');
    });
});
