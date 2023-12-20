import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { generateExampleChannelDTO } from '../helpers/conversationExampleModels';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../../helpers/mocks/service/mock-notification.service';

describe('ChannelService', () => {
    let service: ChannelService;
    let httpMock: HttpTestingController;
    let elemDefault: ChannelDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: NotificationService, useClass: MockNotificationService },
            ],
        });
        service = TestBed.inject(ChannelService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleChannelDTO({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getChannelsOfCourse', fakeAsync(() => {
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
        tick();
    }));

    it('getPublicChannelsOfCourse', fakeAsync(() => {
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
        tick();
    }));

    it('delete', fakeAsync(() => {
        service
            .delete(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, new ChannelDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getChannelOfLecture', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .getChannelOfLecture(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getChannelOfExercise', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .getChannelOfExercise(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, name: 'test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, new ChannelDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('archive', fakeAsync(() => {
        service
            .archive(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('unarchive', fakeAsync(() => {
        service
            .unarchive(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('deregisterUsersFromChannel', fakeAsync(() => {
        service
            .deregisterUsersFromChannel(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('registersUsersToChannel', fakeAsync(() => {
        service
            .registerUsersToChannel(1, 1, true, true, true, ['login'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('grantChannelModeratorRole', fakeAsync(() => {
        service
            .grantChannelModeratorRole(1, 1, ['login'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('revokeChannelModeratorRole', fakeAsync(() => {
        service
            .revokeChannelModeratorRole(1, 1, ['login'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));
});
