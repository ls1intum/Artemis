import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject, distinctUntilChanged, firstValueFrom } from 'rxjs';

import { ProgrammingExerciseWebsocketService } from 'app/programming/manage/services/programming-exercise-websocket.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';

describe('ProgrammingExerciseWebsocketService', () => {
    setupTestBed({ zoneless: true });

    let service: ProgrammingExerciseWebsocketService;
    let websocketService: WebsocketService;
    let topicSubject: Subject<boolean>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        service = TestBed.inject(ProgrammingExerciseWebsocketService);
        websocketService = TestBed.inject(WebsocketService);

        topicSubject = new Subject<boolean>();
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(topicSubject.asObservable());
    });

    it('should subscribe to the test-cases-changed topic and emit values', async () => {
        const observable = service.getTestCaseState(7);
        const next = firstValueFrom(observable);

        topicSubject.next(true);

        expect(await next).toBe(true);
        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/programming-exercises/7/test-cases-changed');
    });

    it('should reuse the existing subject for repeated calls', () => {
        service.getTestCaseState(7);
        service.getTestCaseState(7);

        expect(websocketService.subscribe).toHaveBeenCalledOnce();
    });

    describe('authentication state changes', () => {
        let authState: BehaviorSubject<User | undefined>;
        let scoped: ProgrammingExerciseWebsocketService;
        let scopedWs: WebsocketService;

        beforeEach(() => {
            authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
            const customAccountService = new MockAccountService();
            customAccountService.userIdentity.set({ id: 99 } as User);
            customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [
                    { provide: WebsocketService, useClass: MockWebsocketService },
                    { provide: AccountService, useValue: customAccountService },
                ],
            });
            scoped = TestBed.inject(ProgrammingExerciseWebsocketService);
            scopedWs = TestBed.inject(WebsocketService);
            vi.spyOn(scopedWs, 'subscribe').mockReturnValue(new Subject<boolean>().asObservable());
        });

        it('should tear down subscriptions and complete subjects on logout', () => {
            const subject = scoped.getTestCaseState(7);
            let completed = false;
            subject.subscribe({ complete: () => (completed = true) });

            authState.next(undefined);

            expect(completed).toBe(true);
            // After reset, getTestCaseState should re-subscribe via the websocket service.
            scoped.getTestCaseState(7);
            expect(scopedWs.subscribe).toHaveBeenCalledTimes(2);
        });

        it('should not tear down subscriptions when the same user re-emits', () => {
            scoped.getTestCaseState(7);
            authState.next({ id: 99 } as User);
            scoped.getTestCaseState(7);
            expect(scopedWs.subscribe).toHaveBeenCalledOnce();
        });
    });
});
