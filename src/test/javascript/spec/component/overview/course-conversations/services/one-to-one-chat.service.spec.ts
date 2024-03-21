import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { generateOneToOneChatDTO } from '../helpers/conversationExampleModels';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { OneToOneChatService } from 'app/shared/metis/conversations/one-to-one-chat.service';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../../helpers/mocks/service/mock-notification.service';

describe('OneToOneChatService', () => {
    let service: OneToOneChatService;
    let httpMock: HttpTestingController;
    let elemDefault: OneToOneChatDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: NotificationService, useClass: MockNotificationService },
            ],
        });
        service = TestBed.inject(OneToOneChatService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateOneToOneChatDTO({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('create', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, 'login')
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));
});
