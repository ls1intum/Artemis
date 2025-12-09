import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { generateExampleGroupChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { provideHttpClient } from '@angular/common/http';

describe('GroupChatService', () => {
    let service: GroupChatService;
    let httpMock: HttpTestingController;
    let elemDefault: GroupChatDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        service = TestBed.inject(GroupChatService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleGroupChatDTO({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('create', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(1, ['user1', 'user2'])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { name: 'test' });
        const expected = Object.assign({}, returnedFromService);

        service
            .update(1, 1, new GroupChatDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('removeUsersFromGroupChat', fakeAsync(() => {
        service
            .removeUsersFromGroupChat(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('addUsersToGroupChat', fakeAsync(() => {
        service
            .addUsersToGroupChat(1, 1, ['user1', 'user2'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));
});
