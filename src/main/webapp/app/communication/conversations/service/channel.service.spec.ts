import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import type { Channel } from 'app/openapi/model/channel';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

describe('ChannelService', () => {
    const base = '/api/communication/courses/1';
    const wireChannel: Channel = {
        id: 2,
        type: 'channel',
        name: 'exercise-channel',
        subType: 'exercise',
        subTypeReferenceId: 3,
        isPublic: true,
        isAnnouncementChannel: false,
        isArchived: false,
        isCourseWide: false,
        isChannelModerator: false,
        hasChannelModerationRights: false,
        creator: { id: 10, login: 'instructor', isInstructor: true },
        creationDate: '2026-01-01T09:00:00Z',
        lastMessageDate: '2026-01-01T10:00:00Z',
        lastReadDate: '2026-01-01T11:00:00Z',
        subTypeReferenceStartDate: '2026-01-02T09:00:00Z',
        subTypeReferenceEndDate: '2026-01-03T09:00:00Z',
    };

    let service: ChannelService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useValue: { userIdentity: () => ({ login: 'self' }) } }],
        });
        service = TestBed.inject(ChannelService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it.each([
        ['getChannelsOfCourse', ''],
        ['getChannelsOfCourseForStaff', '/for-staff'],
        ['getChannelsOfCourseForInstructor', '/for-instructor'],
    ] as const)('loads and hydrates the overview through %s', async (method, suffix) => {
        const request$ = service[method](1);
        httpMock.expectNone(base + '/channels/overview' + suffix);
        const result = firstValueFrom(request$);
        httpMock.expectOne({ method: 'GET', url: base + '/channels/overview' + suffix }).flush([wireChannel]);
        const response = await result;
        expect(response.body).toHaveLength(1);
        const channel = response.body?.[0];
        expect(channel).toBeInstanceOf(ChannelDTO);
        expect(channel?.type).toBe(ConversationType.CHANNEL);
        expect(channel?.subType).toBe(ChannelSubType.EXERCISE);
        expect(channel?.name).toBe(wireChannel.name);
        expect(channel?.creator).toBeInstanceOf(ConversationUserDTO);
        expect(channel?.creator?.login).toBe('instructor');
        expect(channel?.creationDate?.toISOString()).toBe('2026-01-01T09:00:00.000Z');
        expect(channel?.lastMessageDate?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
        expect(channel?.lastReadDate?.toISOString()).toBe('2026-01-01T11:00:00.000Z');
        expect(channel?.subTypeReferenceStartDate?.toISOString()).toBe('2026-01-02T09:00:00.000Z');
        expect(channel?.subTypeReferenceEndDate?.toISOString()).toBe('2026-01-03T09:00:00.000Z');
    });

    it('loads public channel IDs and names', async () => {
        const result = firstValueFrom(service.getPublicChannelsOfCourse(1));
        const channels = [{ id: 2, name: 'public' }];
        httpMock.expectOne({ method: 'GET', url: base + '/channels/public-overview' }).flush(channels);
        expect((await result).body).toEqual(channels);
    });

    it.each([
        ['getChannelOfExercise', '/exercises/3/channel'],
        ['getChannelOfLecture', '/lectures/3/channel'],
    ] as const)('loads a discussion channel or preserves an empty 200 through %s', async (method, path) => {
        const result = firstValueFrom(service[method](1, 3));
        httpMock.expectOne({ method: 'GET', url: base + path }).flush(wireChannel);
        const response = await result;
        expect(response.body).toBeInstanceOf(ChannelDTO);
        expect(response.body?.id).toBe(2);

        const emptyResult = firstValueFrom(service[method](1, 3));
        httpMock.expectOne({ method: 'GET', url: base + path }).flush(null, { status: 200, statusText: 'OK' });
        expect(await emptyResult).toMatchObject({ status: 200, body: null });
    });

    it('passes through the discussion 403 with skipAlert', async () => {
        const result = firstValueFrom(service.getChannelOfExercise(1, 3));
        const rejection = expect(result).rejects.toMatchObject({ status: 403, error: { skipAlert: true, message: 'error.noAccessButCouldJoin' } });
        httpMock
            .expectOne({ method: 'GET', url: base + '/exercises/3/channel' })
            .flush({ skipAlert: true, message: 'error.noAccessButCouldJoin' }, { status: 403, statusText: 'Forbidden' });
        await rejection;
        await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    });

    it('creates a channel with only writable fields and preserves the 201 response', async () => {
        const channel = new ChannelDTO();
        channel.id = 42;
        channel.name = 'exercise-channel';
        channel.isPublic = true;
        channel.hasChannelModerationRights = true;

        const result = firstValueFrom(service.create(1, channel));
        const req = httpMock.expectOne({ method: 'POST', url: base + '/channels' });
        expect(req.request.serializeBody()).toBe(
            JSON.stringify({ type: 'channel', name: 'exercise-channel', isPublic: true, isAnnouncementChannel: false, isArchived: false, isCourseWide: false }),
        );
        req.flush(wireChannel, { status: 201, statusText: 'Created', headers: { Location: '/api/channels/2' } });
        const response = await result;
        expect(response).toBeInstanceOf(HttpResponse);
        expect(response.status).toBe(201);
        expect(response.headers.get('Location')).toBe('/api/channels/2');
        expect(response.body).toBeInstanceOf(ChannelDTO);
        expect(response.body?.id).toBe(2);
    });

    it('updates a channel with the subtype and the writable fields', async () => {
        const channel = new ChannelDTO();
        channel.name = 'renamed';
        channel.isMember = true;
        const result = firstValueFrom(service.update(1, 2, channel));
        const req = httpMock.expectOne({ method: 'PUT', url: base + '/channels/2' });
        expect(req.request.serializeBody()).toBe(
            JSON.stringify({ type: 'channel', name: 'renamed', isPublic: false, isAnnouncementChannel: false, isArchived: false, isCourseWide: false }),
        );
        req.flush({ ...wireChannel, name: 'renamed' });
        expect((await result).body?.name).toBe('renamed');
    });

    it.each([
        ['delete', 'DELETE', '', null],
        ['archive', 'POST', '/archive', null],
        ['unarchive', 'POST', '/unarchive', null],
        ['deregisterUsersFromChannel', 'POST', '/deregister', ['self']],
        ['grantChannelModeratorRole', 'POST', '/grant-channel-moderator', ['self']],
        ['revokeChannelModeratorRole', 'POST', '/revoke-channel-moderator', ['self']],
    ] as const)('preserves the bodyless 200 and request body for %s', async (method, verb, suffix, requestBody) => {
        const result = firstValueFrom(service[method](1, 2));
        const req = httpMock.expectOne({ method: verb, url: base + '/channels/2' + suffix });
        expect(req.request.body).toEqual(requestBody);
        req.flush(null, { status: 200, statusText: 'OK', headers: { 'X-Frame-Options': 'DENY' } });
        const response = await result;
        expect(response).toBeInstanceOf(HttpResponse);
        expect(response).toMatchObject({ status: 200, body: null });
        expect(response.headers.get('X-Frame-Options')).toBe('DENY');
    });

    it.each([
        ['grantChannelModeratorRole', 'grant-channel-moderator'],
        ['revokeChannelModeratorRole', 'revoke-channel-moderator'],
        ['deregisterUsersFromChannel', 'deregister'],
    ] as const)('sends explicitly supplied logins for %s', async (method, suffix) => {
        const result = firstValueFrom(service[method](1, 2, ['other']));
        const req = httpMock.expectOne({ method: 'POST', url: base + '/channels/2/' + suffix });
        expect(req.request.body).toEqual(['other']);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect((await result).body).toBeNull();
    });

    it('registers explicitly supplied logins and sends only true flags', async () => {
        const result = firstValueFrom(service.registerUsersToChannel(1, 2, true, false, true, ['other']));
        const req = httpMock.expectOne((request) => request.method === 'POST' && request.url.split('?')[0] === base + '/channels/2/register');
        const query = new URL(req.request.urlWithParams, 'http://localhost').searchParams;
        expect(Array.from(query.entries())).toEqual([
            ['addAllStudents', 'true'],
            ['addAllInstructors', 'true'],
        ]);
        expect(req.request.body).toEqual(['other']);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect((await result).body).toBeNull();
    });

    it('registers the requesting user with no query parameters when all flags default to false', async () => {
        const result = firstValueFrom(service.registerUsersToChannel(1, 2));
        const req = httpMock.expectOne({ method: 'POST', url: base + '/channels/2/register' });
        expect(req.request.body).toEqual(['self']);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect((await result).body).toBeNull();
    });

    it('toggles privacy with a bodyless POST and maps the returned channel', async () => {
        const result = firstValueFrom(service.toggleChannelPrivacy(1, 2));
        const req = httpMock.expectOne({ method: 'POST', url: base + '/channels/2/toggle-privacy' });
        expect(req.request.body).toBeNull();
        req.flush({ ...wireChannel, isPublic: false });
        const response = await result;
        expect(response.body).toBeInstanceOf(ChannelDTO);
        expect(response.body?.isPublic).toBe(false);
    });
});
