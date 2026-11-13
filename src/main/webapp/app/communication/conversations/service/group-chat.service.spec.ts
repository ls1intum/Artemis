import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { AccountService } from 'app/core/auth/account.service';
import type { GroupChat } from 'app/openapi/model/group-chat';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

describe('GroupChatService', () => {
    const base = '/api/communication/courses/1/group-chats';
    const wireChat: GroupChat = {
        id: 2,
        type: 'groupChat',
        creationDate: '2026-01-01T09:00:00Z',
        lastReadDate: '2026-01-01T10:00:00Z',
        members: [{ login: 'self', isRequestingUser: true }, { login: 'other' }],
    };
    let service: GroupChatService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useValue: { userIdentity: () => ({ login: 'self' }) } }],
        });
        service = TestBed.inject(GroupChatService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('creates a group chat and preserves the 201 response while hydrating the group and its members', async () => {
        const result = firstValueFrom(service.create(1, ['other', 'third']));
        const req = httpMock.expectOne({ method: 'POST', url: base });
        expect(req.request.body).toEqual({ memberLogins: ['other', 'third'] });
        req.flush(wireChat, { status: 201, statusText: 'Created', headers: { Location: '/api/group-chats/2' } });
        const response = await result;
        expect(response).toBeInstanceOf(HttpResponse);
        expect(response.status).toBe(201);
        expect(response.headers.get('Location')).toBe('/api/group-chats/2');
        expect(response.body).toBeInstanceOf(GroupChatDTO);
        expect(response.body?.id).toBe(2);
        expect(response.body?.type).toBe(ConversationType.GROUP_CHAT);
        expect(response.body?.name).toBeUndefined();
        expect(response.body?.creator).toBeUndefined();
        expect(response.body?.members?.map((member) => member.login)).toEqual(['self', 'other']);
        expect(response.body?.members?.[0]).toBeInstanceOf(ConversationUserDTO);
        expect(response.body?.members?.[0].isRequestingUser).toBe(true);
        expect(response.body?.creationDate?.toISOString()).toBe('2026-01-01T09:00:00.000Z');
        expect(response.body?.lastReadDate?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
    });

    it('updates only the group chat name and sends the subtype', async () => {
        const chat = new GroupChatDTO();
        chat.name = 'renamed';
        chat.id = 2;
        const result = firstValueFrom(service.update(1, 2, chat));
        const req = httpMock.expectOne({ method: 'PUT', url: base + '/2' });
        expect(req.request.body).toEqual({ type: 'groupChat', name: 'renamed' });
        req.flush({ ...wireChat, name: 'renamed' });
        const response = await result;
        expect(response.body).toBeInstanceOf(GroupChatDTO);
        expect(response.body?.name).toBe('renamed');
    });

    it.each([
        { logins: undefined, body: ['self'] },
        { logins: ['other'], body: ['other'] },
    ])('deregisters $body', async ({ logins, body }) => {
        const result = firstValueFrom(service.removeUsersFromGroupChat(1, 2, logins));
        const req = httpMock.expectOne({ method: 'POST', url: base + '/2/deregister' });
        expect(req.request.body).toEqual(body);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect(await result).toMatchObject({ status: 200, body: null });
    });

    it('registers the supplied users', async () => {
        const result = firstValueFrom(service.addUsersToGroupChat(1, 2, ['other', 'third']));
        const req = httpMock.expectOne({ method: 'POST', url: base + '/2/register' });
        expect(req.request.body).toEqual(['other', 'third']);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect(await result).toMatchObject({ status: 200, body: null });
    });
});
