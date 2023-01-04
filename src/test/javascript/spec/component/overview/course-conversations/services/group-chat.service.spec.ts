import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { generateExampleGroupChatDTO } from '../helpers/conversationExampleModels';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';

describe('GroupChatService', () => {
    let service: GroupChatService;
    let httpMock: HttpTestingController;
    let elemDefault: GroupChatDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
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
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, ['user1', 'user2'])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, name: 'test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, new GroupChatDto())
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
