import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
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
    let service: ChannelService;
    let httpMock: HttpTestingController;
    let elemDefault: ChannelDTO;

    beforeEach(() => {
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
        httpMock.verify();
    });

    it('getChannelsOfCourse', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { title: 'Test' });
        const expected = Object.assign({}, returnedFromService);

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
        const returnedFromService = Object.assign({}, elemDefault, { title: 'Test' });
        const expected = Object.assign({}, returnedFromService);

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
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(1, new ChannelDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getChannelOfLecture', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .getChannelOfLecture(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getChannelOfExercise', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .getChannelOfExercise(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { name: 'test' });
        const expected = Object.assign({}, returnedFromService);

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

    it('toggleChannelPrivacy', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { isPublic: false });
        const expected = Object.assign({}, returnedFromService);

        service
            .toggleChannelPrivacy(1, 2)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST', url: '/api/communication/courses/1/channels/2/toggle-privacy' });
        req.flush(returnedFromService);
        tick();
    }));
});
