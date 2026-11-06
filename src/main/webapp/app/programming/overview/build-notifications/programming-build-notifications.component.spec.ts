import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { ProgrammingBuildNotificationsComponent } from './programming-build-notifications.component';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';

describe('ProgrammingBuildNotificationsComponent', () => {
    let fixture: ComponentFixture<ProgrammingBuildNotificationsComponent>;
    let submissions: Subject<ProgrammingSubmissionStateObj>;
    let results: BehaviorSubject<Result | undefined>;
    let icons: HTMLLinkElement[];
    let hidden: boolean;
    let focused: boolean;
    let permission: NotificationPermission;
    let requestPermission = vi.fn<() => Promise<NotificationPermission>>();
    let showNotification = vi.fn<(title: string, options: NotificationOptions, notification: object) => void>();
    let closeNotification = vi.fn<() => void>();
    const originalServiceWorker = Object.getOwnPropertyDescriptor(navigator, 'serviceWorker');
    const user = signal({ id: 7, login: 'student' });
    const exercise = { id: 1, title: 'Sorting', type: ExerciseType.PROGRAMMING };
    const participation = { id: 2, type: ParticipationType.PROGRAMMING };

    function state(submissionState: ProgrammingSubmissionState, id = 3, buildStartDate?: dayjs.Dayjs) {
        submissions.next({ participationId: 2, submissionState, submission: { id }, buildTimingInfo: { buildStartDate } });
        fixture.detectChanges();
    }

    function complete(id = 3, resultId = 4) {
        results.next({ id: resultId, submission: { id }, successful: false });
        fixture.detectChanges();
    }

    function button(): HTMLButtonElement {
        return fixture.nativeElement.querySelector('[data-testid="build-notifications-toggle"]');
    }

    async function enableNotifications() {
        button().click();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    beforeEach(() => {
        submissions = new Subject();
        results = new BehaviorSubject<Result | undefined>(undefined);
        hidden = true;
        focused = false;
        permission = 'default';
        user.set({ id: 7, login: 'student' });
        localStorage.clear();
        vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
        vi.spyOn(document, 'hasFocus').mockImplementation(() => focused);
        vi.stubGlobal('isSecureContext', true);
        requestPermission = vi.fn(async () => (permission = 'granted'));
        closeNotification = vi.fn();
        showNotification = vi.fn();
        class BrowserNotification {
            static get permission() {
                return permission;
            }
            static requestPermission = requestPermission;
            onclick?: () => void;
            close = closeNotification;
            constructor(title: string, options: NotificationOptions) {
                showNotification(title, options, this);
            }
        }
        vi.stubGlobal('Notification', BrowserNotification);
        icons = ['image/svg+xml', 'image/png'].map((type, index) => {
            const icon = document.createElement('link');
            icon.rel = 'icon';
            icon.type = type;
            icon.setAttribute('href', 'logo/original-' + index + (index ? '.png' : '.svg'));
            icon.setAttribute('sizes', index ? '32x32' : 'any');
            document.head.appendChild(icon);
            return icon;
        });
        TestBed.configureTestingModule({
            imports: [ProgrammingBuildNotificationsComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useValue: { userIdentity: user } },
                {
                    provide: ProgrammingSubmissionService,
                    useValue: { getLatestPendingSubmissionByParticipationId: () => submissions, unsubscribeForLatestSubmissionOfParticipation: vi.fn() },
                },
                { provide: ParticipationWebsocketService, useValue: { subscribeForLatestResultOfParticipation: () => results } },
            ],
        });
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        icons.forEach((icon) => icon.remove());
        localStorage.clear();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        if (originalServiceWorker) {
            Object.defineProperty(navigator, 'serviceWorker', originalServiceWorker);
        } else {
            Reflect.deleteProperty(navigator, 'serviceWorker');
        }
    });

    it('changes every favicon to a progress icon when the build animation starts', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        expect(icons.every((icon) => icon.getAttribute('href')?.includes('favicon-building'))).toBe(true);
        expect(requestPermission).not.toHaveBeenCalled();
    });

    it('shows progress for queued builds too', () => {
        state(ProgrammingSubmissionState.IS_QUEUED);
        expect(icons[0].href).toContain('favicon-building');
    });

    it('shows result received for the matching completed build, including unsuccessful results', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        state(ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION);
        complete();
        expect(icons[0].href).toContain('favicon-result');
        expect(showNotification).not.toHaveBeenCalled();
    });

    it('ignores cached results and incoming results for other submissions', () => {
        complete(1);
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete(42);
        expect(icons[0].href).toContain('favicon-building');
    });

    it('does not treat a build timeout as a received result', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        state(ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
    });

    it('still notifies when a real matching result arrives after a timeout', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        state(ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        expect(showNotification).not.toHaveBeenCalled();
        complete();
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-result');
        expect(showNotification).toHaveBeenCalledOnce();
    });

    it('keeps the normal icon when the result arrives in the focused tab', () => {
        hidden = false;
        focused = true;
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
    });

    it('restores the original favicon attributes when the user opens the tab', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        hidden = false;
        focused = true;
        document.dispatchEvent(new Event('visibilitychange'));
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
        expect(icons[1].getAttribute('href')).toBe('logo/original-1.png');
        expect(icons[1].type).toBe('image/png');
        expect(icons[1].getAttribute('sizes')).toBe('32x32');
    });

    it('restores the normal icon when the browser window regains focus', () => {
        hidden = false;
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        focused = true;
        window.dispatchEvent(new Event('focus'));
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
    });

    it('restores icons and stops observing builds after leaving the page', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        fixture.destroy();
        results.next({ id: 4, submission: { id: 3 } });
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
        expect(submissions.observed).toBe(false);
        expect(results.observed).toBe(false);
    });

    it('clears pending build state when the active participation changes', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        fixture.componentRef.setInput('participation', { id: 9, type: ParticipationType.PROGRAMMING });
        fixture.detectChanges();
        complete();
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
    });

    it('keeps tracking the build when the same participation gets updated', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        fixture.componentRef.setInput('participation', { ...participation, submissions: [] });
        fixture.detectChanges();
        complete();
        expect(icons[0].href).toContain('favicon-result');
    });

    it('requests permission only from the opt-in button and sends one notification per build', async () => {
        expect(requestPermission).not.toHaveBeenCalled();
        await enableNotifications();
        expect(requestPermission).toHaveBeenCalledOnce();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        complete();
        await fixture.whenStable();
        expect(showNotification).toHaveBeenCalledOnce();
        expect(showNotification.mock.calls[0][1]).toMatchObject({ silent: true, tag: 'artemis-build-7-2-3-4' });
        expect(button().getAttribute('aria-pressed')).toBe('true');
    });

    it('closes the system notification on returning to the tab', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        hidden = false;
        focused = true;
        window.dispatchEvent(new Event('focus'));
        expect(closeNotification).toHaveBeenCalledOnce();
    });

    it('lets the user turn notifications off without another permission prompt', async () => {
        await enableNotifications();
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        expect(showNotification).not.toHaveBeenCalled();
        expect(requestPermission).toHaveBeenCalledOnce();
        expect(button().getAttribute('aria-pressed')).toBe('false');
    });

    it('keeps the favicon fallback when notification permission is denied', async () => {
        requestPermission.mockImplementation(async () => (permission = 'denied'));
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-result');
        expect(showNotification).not.toHaveBeenCalled();
        expect(button().disabled).toBe(true);
    });

    it('does not send system notifications while the exercise tab has focus', async () => {
        await enableNotifications();
        hidden = false;
        focused = true;
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        expect(showNotification).not.toHaveBeenCalled();
    });

    it('retains favicon behavior when the Notifications API is unavailable', () => {
        vi.stubGlobal('Notification', undefined);
        fixture.destroy();
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        expect(button()).toBeNull();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        expect(icons[0].href).toContain('favicon-result');
    });

    it('does not retain a previous user’s build or opt-in state', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        user.set({ id: 8, login: 'another-student' });
        fixture.detectChanges();
        complete();
        await fixture.whenStable();
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
        expect(button().getAttribute('aria-pressed')).toBe('false');
        expect(showNotification).not.toHaveBeenCalled();
    });

    it('accepts opt-in if a build starts or the tab regains focus while permission is pending', async () => {
        let resolvePermission!: (permission: NotificationPermission) => void;
        requestPermission.mockImplementation(() => new Promise<NotificationPermission>((resolve) => (resolvePermission = resolve)));
        button().click();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        hidden = false;
        focused = true;
        window.dispatchEvent(new Event('focus'));
        permission = 'granted';
        resolvePermission(permission);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(button().getAttribute('aria-pressed')).toBe('true');
    });

    it('does not save an opt-in that resolves after leaving the page', async () => {
        let resolvePermission!: (permission: NotificationPermission) => void;
        requestPermission.mockImplementation(() => new Promise<NotificationPermission>((resolve) => (resolvePermission = resolve)));
        button().click();
        fixture.destroy();
        resolvePermission('granted');
        await Promise.resolve();
        expect(localStorage.getItem('buildNotifications.7')).toBeNull();
    });

    it('remembers the current user’s opt-in on revisiting the exercise', async () => {
        await enableNotifications();
        fixture.destroy();
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        expect(button().getAttribute('aria-pressed')).toBe('true');
        expect(requestPermission).toHaveBeenCalledOnce();
    });

    it('uses the service worker for supported mobile platforms and supplies a result click destination', async () => {
        const display = vi.fn().mockResolvedValue(undefined);
        const close = vi.fn();
        Object.defineProperty(navigator, 'serviceWorker', {
            configurable: true,
            value: { getRegistration: async () => ({ active: {}, showNotification: display, getNotifications: async () => [{ close }] }) },
        });
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        expect(display).toHaveBeenCalledOnce();
        expect(display.mock.calls[0][1]).toMatchObject({
            silent: true,
            data: { onActionClick: { default: { operation: 'navigateLastFocusedOrOpen', url: document.location.href } } },
        });
        expect(showNotification).not.toHaveBeenCalled();
        fixture.destroy();
        await Promise.resolve();
        expect(close).toHaveBeenCalledOnce();
    });

    it('does not display a delayed notification after leaving the exercise', async () => {
        const display = vi.fn();
        let resolveRegistration!: (registration: { showNotification: typeof display }) => void;
        Object.defineProperty(navigator, 'serviceWorker', {
            configurable: true,
            value: { getRegistration: () => new Promise((resolve) => (resolveRegistration = resolve)) },
        });
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        fixture.destroy();
        resolveRegistration({ showNotification: display });
        await Promise.resolve();
        expect(display).not.toHaveBeenCalled();
    });

    it('keeps showing the result favicon if native notification delivery fails', async () => {
        showNotification.mockImplementation(() => {
            throw new TypeError('Notifications require a service worker on this platform');
        });
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(icons[0].href).toContain('favicon-result');
        expect(button().getAttribute('aria-label')).toBe('artemisApp.programmingExercise.buildNotifications.error');
    });

    it('reconciles a result received before the pending-submission request finishes', async () => {
        await enableNotifications();
        complete();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        state(ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION);
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-result');
        expect(showNotification).toHaveBeenCalledOnce();
    });

    it('tracks a rebuild of the same submission without reusing its previous result', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        expect(showNotification).toHaveBeenCalledOnce();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        expect(icons[0].href).toContain('favicon-building');
        expect(showNotification).toHaveBeenCalledOnce();
        await fixture.whenStable();
        complete();
        state(ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION);
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-building');
        complete(3, 5);
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-result');
        expect(showNotification).toHaveBeenCalledTimes(2);
    });

    it('does not mistake the initially cached result for a same-submission rebuild result', () => {
        fixture.destroy();
        results = new BehaviorSubject<Result | undefined>({ id: 4, submission: { id: 3 } });
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        expect(icons[0].href).toContain('favicon-building');
        complete(3, 5);
        expect(icons[0].href).toContain('favicon-result');
    });

    it('restores progress from an obsolete initial snapshot without notifying for an old cached result', async () => {
        fixture.destroy();
        results = new BehaviorSubject<Result | undefined>({ id: 4, submission: { id: 3 } });
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        state(ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION);
        await fixture.whenStable();
        expect(icons[0].getAttribute('href')).toBe('logo/original-0.svg');
        expect(showNotification).not.toHaveBeenCalled();
    });

    it('accepts a newer completion of a semi-automatic result updated in place', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        const result: Result = { id: 4, completionDate: dayjs('2026-11-06T10:00:00Z'), submission: { id: 3 } };
        results.next(result);
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        result.completionDate = dayjs('2026-11-06T10:01:00Z');
        results.next(result);
        fixture.detectChanges();
        expect(icons[0].href).toContain('favicon-result');
    });

    it('keeps the building favicon when system notifications are disabled', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        await enableNotifications();
        expect(icons[0].href).toContain('favicon-building');
        complete();
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-result');
        expect(showNotification).not.toHaveBeenCalled();
    });

    it('still closes notifications when persisting opt-out fails', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new DOMException('Storage is full', 'QuotaExceededError');
        });
        await enableNotifications();
        expect(closeNotification).toHaveBeenCalledOnce();
        expect(button().getAttribute('aria-pressed')).toBe('false');
    });

    it('honors an opt-out from another tab without removing build feedback', async () => {
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        localStorage.setItem('buildNotifications.7', 'false');
        window.dispatchEvent(new StorageEvent('storage', { key: 'buildNotifications.7', newValue: 'false' }));
        fixture.detectChanges();
        expect(button().getAttribute('aria-pressed')).toBe('false');
        expect(icons[0].href).toContain('favicon-building');
        complete();
        await fixture.whenStable();
        expect(showNotification).not.toHaveBeenCalled();
    });

    it('does not let an older permission prompt override another tab’s opt-out', async () => {
        let resolvePermission!: (permission: NotificationPermission) => void;
        requestPermission.mockImplementation(() => new Promise<NotificationPermission>((resolve) => (resolvePermission = resolve)));
        button().click();
        localStorage.setItem('buildNotifications.7', 'false');
        window.dispatchEvent(new StorageEvent('storage', { key: 'buildNotifications.7', newValue: 'false' }));
        permission = 'granted';
        resolvePermission(permission);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(button().getAttribute('aria-pressed')).toBe('false');
        expect(localStorage.getItem('buildNotifications.7')).toBe('false');
    });

    it('refreshes permission when another tab enables notifications', () => {
        permission = 'granted';
        localStorage.setItem('buildNotifications.7', 'true');
        window.dispatchEvent(new StorageEvent('storage', { key: 'buildNotifications.7', newValue: 'true' }));
        fixture.detectChanges();
        expect(button().getAttribute('aria-pressed')).toBe('true');
    });

    it('retains favicon tracking when preference storage is unavailable', () => {
        fixture.destroy();
        const originalGetItem = Storage.prototype.getItem;
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) {
            if (key.startsWith('buildNotifications.')) {
                throw new DOMException('Storage is blocked', 'SecurityError');
            }
            return originalGetItem.call(this, key);
        });
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        expect(() => fixture.detectChanges()).not.toThrow();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        expect(icons[0].href).toContain('favicon-result');
    });

    it('falls back to a page notification while the service worker is installing', async () => {
        const display = vi.fn().mockRejectedValue(new TypeError('No active worker'));
        Object.defineProperty(navigator, 'serviceWorker', {
            configurable: true,
            value: { getRegistration: async () => ({ active: null, showNotification: display, getNotifications: async () => [] }) },
        });
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        expect(display).not.toHaveBeenCalled();
        expect(showNotification).toHaveBeenCalledOnce();
    });

    it('does not let delayed cleanup of one build close a later build notification', async () => {
        let finishFirstDisplay!: () => void;
        const closeFirst = vi.fn();
        const closeSecond = vi.fn();
        const tags: string[] = [];
        const notifications = new Map<string, { close: () => void }>();
        const display = vi.fn((_title: string, options: NotificationOptions) => {
            tags.push(options.tag!);
            notifications.set(options.tag!, { close: tags.length === 1 ? closeFirst : closeSecond });
            return tags.length === 1 ? new Promise<void>((resolve) => (finishFirstDisplay = resolve)) : Promise.resolve();
        });
        Object.defineProperty(navigator, 'serviceWorker', {
            configurable: true,
            value: {
                getRegistration: async () => ({
                    active: {},
                    showNotification: display,
                    getNotifications: async ({ tag }: { tag: string }) => (notifications.has(tag) ? [notifications.get(tag)!] : []),
                }),
            },
        });
        await enableNotifications();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        await fixture.whenStable();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete(3, 5);
        await fixture.whenStable();
        expect(tags).toHaveLength(2);
        expect(tags[0]).not.toBe(tags[1]);
        finishFirstDisplay();
        await fixture.whenStable();
        await Promise.resolve();
        expect(closeSecond).not.toHaveBeenCalled();
    });

    it('does not apply an older cached completion to a build that started later', async () => {
        results.next({ id: 4, completionDate: dayjs('2026-11-06T10:00:00Z'), submission: { id: 3 } });
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, 3, dayjs('2026-11-06T10:01:00Z'));
        state(ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION);
        await fixture.whenStable();
        expect(icons[0].href).toContain('favicon-building');
        results.next({ id: 5, completionDate: dayjs('2026-11-06T10:02:00Z'), submission: { id: 3 } });
        fixture.detectChanges();
        expect(icons[0].href).toContain('favicon-result');
    });

    it('handles an initial replay when build timing proves it completed this pending attempt', () => {
        fixture.destroy();
        results = new BehaviorSubject<Result | undefined>({ id: 4, completionDate: dayjs('2026-11-06T10:01:00Z'), submission: { id: 3 } });
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, 3, dayjs('2026-11-06T10:00:00Z'));
        expect(icons[0].href).toContain('favicon-result');
    });

    it('ignores a snapshot of a handled attempt while permitting a newer same-submission build', () => {
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, 3, dayjs('2026-11-06T10:00:00Z'));
        results.next({ id: 4, completionDate: dayjs('2026-11-06T10:01:00Z'), submission: { id: 3 } });
        fixture.detectChanges();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, 3, dayjs('2026-11-06T10:00:00Z'));
        expect(icons[0].href).toContain('favicon-result');
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, 3, dayjs('2026-11-06T10:02:00Z'));
        expect(icons[0].href).toContain('favicon-building');
    });

    it('uses the participation snapshot as its baseline before any websocket result has arrived', () => {
        fixture.destroy();
        fixture = TestBed.createComponent(ProgrammingBuildNotificationsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { ...participation, submissions: [{ id: 3, results: [{ id: 4 }] }] });
        fixture.detectChanges();
        state(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        complete();
        expect(icons[0].href).toContain('favicon-building');
        complete(3, 5);
        expect(icons[0].href).toContain('favicon-result');
    });
});
