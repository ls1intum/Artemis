import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { generateExampleChannelDTO } from 'test/helpers/sample/conversationExampleModels';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';

describe('ChannelService', () => {
    setupTestBed({ zoneless: true });

    let service: ChannelService;
    let httpMock: HttpTestingController;
    let elemDefault: ChannelDTO;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        service = TestBed.inject(ChannelService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleChannelDTO({} as ChannelDTO);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('getChannelsOfCourse', () => {
        const returnedFromService = { ...elemDefault, title: 'Test' };
        const expected = { ...returnedFromService };

        service
            .getChannelsOfCourse(1)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        vi.advanceTimersByTime(0);
    });

    it('getPublicChannelsOfCourse', () => {
        const returnedFromService = { ...elemDefault, title: 'Test' };
        const expected = { ...returnedFromService };

        service
            .getPublicChannelsOfCourse(1)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        vi.advanceTimersByTime(0);
    });

    it('delete', () => {
        service
            .delete(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('create', () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, new ChannelDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });

    it('getChannelOfLecture', () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .getChannelOfLecture(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });

    it('getChannelOfExercise', () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .getChannelOfExercise(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });

    it('update', () => {
        const returnedFromService = { ...elemDefault, name: 'test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, new ChannelDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });

    it('archive', () => {
        service
            .archive(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('unarchive', () => {
        service
            .unarchive(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('deregisterUsersFromChannel', () => {
        service
            .deregisterUsersFromChannel(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('registersUsersToChannel', () => {
        service
            .registerUsersToChannel(1, 1, true, true, true, ['login'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('grantChannelModeratorRole', () => {
        service
            .grantChannelModeratorRole(1, 1, ['login'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('revokeChannelModeratorRole', () => {
        service
            .revokeChannelModeratorRole(1, 1, ['login'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('toggleChannelPrivacy', () => {
        const returnedFromService = { ...elemDefault, isPublic: false };
        const expected = { ...returnedFromService };

        service
            .toggleChannelPrivacy(1, 2)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST', url: '/api/communication/courses/1/channels/2/toggle-privacy' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });
});
