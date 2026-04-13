import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
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
    setupTestBed({ zoneless: true });

    let service: GroupChatService;
    let httpMock: HttpTestingController;
    let elemDefault: GroupChatDTO;

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
        service = TestBed.inject(GroupChatService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleGroupChatDTO({});
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('create', () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, ['user1', 'user2'])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });

    it('update', () => {
        const returnedFromService = { ...elemDefault, name: 'test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, new GroupChatDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        vi.advanceTimersByTime(0);
    });

    it('removeUsersFromGroupChat', () => {
        service
            .removeUsersFromGroupChat(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });

    it('addUsersToGroupChat', () => {
        service
            .addUsersToGroupChat(1, 1, ['user1', 'user2'])
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        vi.advanceTimersByTime(0);
    });
});
