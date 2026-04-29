import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { BehaviorSubject, EMPTY, Subject } from 'rxjs';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisChatWebsocketDTO } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-iris-base-chatbot',
    template: '',
    standalone: true,
})
class StubBaseChatbotComponent {}

/**
 * Structural-invariant test: the controller is component-scoped, so two consecutive host mounts
 * MUST yield distinct controller instances with independent state. This is what makes cross-user
 * state leaks impossible — a User-A → User-B login flow tears down the host (and its controller),
 * the new host's controller starts fresh, and there is no shared singleton to leak from.
 *
 * Coverage scope: this spec exercises the invariant via CourseChatbotComponent. The other three
 * provider hosts (ExerciseSplitPanelComponent, IrisExerciseChatbotButtonComponent,
 * TutorSuggestionComponent) provide IrisChatControllerService through the same Angular DI
 * mechanism (`providers: [IrisChatControllerService]` on the @Component decorator), so the
 * fresh-instance-per-mount and destroy-on-unmount semantics are guaranteed by Angular itself
 * — they do not need per-host duplication of this test. The structural-rg invariants in the PR
 * description independently verify that all four hosts declare the provider.
 */
describe('IrisChatControllerService — cross-user isolation invariant (via CourseChatbotComponent)', () => {
    setupTestBed({ zoneless: true });

    const websocketSubscribeSpy = vi.fn().mockReturnValue(EMPTY);
    const websocketServiceMock = {
        subscribe: websocketSubscribeSpy,
        connectionState: new BehaviorSubject({ connected: true, wasEverConnectedBefore: false }),
    } as any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseChatbotComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useValue: websocketServiceMock },
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => true } },
                { provide: AccountService, useValue: { userIdentity: signal<User | undefined>(undefined) } },
                { provide: UserService, useValue: { updateLLMSelectionDecision: () => EMPTY } },
                { provide: Router, useValue: { url: '/courses/1/iris', navigate: vi.fn() } },
                {
                    provide: IrisChatHttpService,
                    useValue: {
                        getCurrentSessionOrCreateIfNotExists: () => EMPTY,
                        createSession: () => EMPTY,
                        getChatSessions: () => EMPTY,
                    },
                },
            ],
        })
            .overrideComponent(CourseChatbotComponent, {
                remove: { imports: [IrisBaseChatbotComponent] },
                add: { imports: [StubBaseChatbotComponent] },
            })
            .compileComponents();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        websocketSubscribeSpy.mockClear();
    });

    it('produces a fresh controller instance per host mount with empty state', () => {
        const fixtureA: ComponentFixture<CourseChatbotComponent> = TestBed.createComponent(CourseChatbotComponent);
        const controllerA = fixtureA.debugElement.injector.get(IrisChatControllerService);

        // User A interacts: seed some state directly via the public BehaviorSubjects.
        controllerA.messages.next([{ id: 1, sender: 'USER', content: [] } as any]);
        controllerA.numNewMessages.next(5);
        controllerA.chatSessions.next([{ id: 9, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1 } as any]);

        fixtureA.destroy();

        // Equivalent of "User B logs in and reaches the same route" — fresh fixture, fresh controller.
        const fixtureB: ComponentFixture<CourseChatbotComponent> = TestBed.createComponent(CourseChatbotComponent);
        const controllerB = fixtureB.debugElement.injector.get(IrisChatControllerService);

        expect(controllerB).not.toBe(controllerA);
        expect(controllerB.messages.getValue()).toEqual([]);
        expect(controllerB.numNewMessages.getValue()).toBe(0);
        expect(controllerB.chatSessions.getValue()).toEqual([]);
        expect(controllerB.sessionId).toBeUndefined();
    });

    it('unsubscribes the websocket subscription on host destruction', () => {
        const fixture: ComponentFixture<CourseChatbotComponent> = TestBed.createComponent(CourseChatbotComponent);
        const controller = fixture.debugElement.injector.get(IrisChatControllerService);

        // Make subscribe() return a Subject so we can observe whether the subscription is closed.
        const channelSubject = new Subject<IrisChatWebsocketDTO>();
        websocketSubscribeSpy.mockReturnValueOnce(channelSubject.asObservable());

        controller.sessionId = 42;
        expect(websocketSubscribeSpy).toHaveBeenCalledWith('/user/topic/iris/42');

        // After fixture.destroy, the takeUntilDestroyed in the controller's chain should close
        // any inner subscription on the channel.
        fixture.destroy();
        expect(channelSubject.observed).toBe(false);
    });

    it('switches the websocket subscription to EMPTY when sessionId becomes undefined', () => {
        const fixture: ComponentFixture<CourseChatbotComponent> = TestBed.createComponent(CourseChatbotComponent);
        const controller = fixture.debugElement.injector.get(IrisChatControllerService);

        const firstSession = new Subject<IrisChatWebsocketDTO>();
        websocketSubscribeSpy.mockReturnValueOnce(firstSession.asObservable());

        controller.sessionId = 7;
        expect(websocketSubscribeSpy).toHaveBeenCalledWith('/user/topic/iris/7');
        expect(firstSession.observed).toBe(true);

        // Closing the active session without a replacement MUST tear down the prior subscription.
        // If the controller ever puts a `filter(id => !!id)` outside the switchMap, this would
        // fail because the undefined emission would be swallowed.
        controller.sessionId = undefined;
        expect(firstSession.observed).toBe(false);
    });

    it('routes payload rateLimitInfo into the controller (the absorbed status path)', () => {
        const fixture: ComponentFixture<CourseChatbotComponent> = TestBed.createComponent(CourseChatbotComponent);
        const controller = fixture.debugElement.injector.get(IrisChatControllerService);

        const channelSubject = new Subject<IrisChatWebsocketDTO>();
        websocketSubscribeSpy.mockReturnValueOnce(channelSubject.asObservable());
        controller.sessionId = 13;

        const newRateLimit = new IrisRateLimitInformation(7, 100, 1);
        channelSubject.next({ rateLimitInfo: newRateLimit } as IrisChatWebsocketDTO);

        expect(controller.currentRatelimitInfoSubject.getValue()).toBe(newRateLimit);
    });
});
