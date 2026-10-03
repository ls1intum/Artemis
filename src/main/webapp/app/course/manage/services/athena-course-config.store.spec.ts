import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AthenaCourseConfigStore } from 'app/course/manage/services/athena-course-config.state';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';

describe('AthenaCourseConfigStore', () => {
    let store: AthenaCourseConfigStore;
    let athenaCourseConfigService: AthenaCourseConfigService;
    let authenticationState: BehaviorSubject<User | undefined>;

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

    beforeEach(() => {
        const user = { id: 1 } as User;
        authenticationState = new BehaviorSubject<User | undefined>(user);

        TestBed.configureTestingModule({
            providers: [
                MockProvider(AthenaCourseConfigService),
                MockProvider(AlertService),
                { provide: AccountService, useValue: { userIdentity: signal(user), getAuthenticationState: () => authenticationState.asObservable() } },
            ],
        });

        store = TestBed.inject(AthenaCourseConfigStore);
        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return the same instance for the same course id', () => {
        expect(store.getOrCreate(5)).toBe(store.getOrCreate(5));
    });

    it('should return a different instance for a different course id', () => {
        expect(store.getOrCreate(6)).not.toBe(store.getOrCreate(5));
    });

    it('should not fetch anything just by creating the state', () => {
        // getOrCreate is called from a computed, which must stay free of side effects such as an HTTP request.
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));

        store.getOrCreate(5);

        expect(getSpy).not.toHaveBeenCalled();
    });

    it('should only fetch once while a load for the same course is still in flight', () => {
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(new Subject<AthenaCourseConfigDTO>().asObservable());

        store.getOrCreate(5).ensureLoaded();
        store.getOrCreate(5).ensureLoaded();

        expect(getSpy).toHaveBeenCalledOnce();
    });

    it('should show the cached configuration right away and revalidate it on a later mount', () => {
        const response = new Subject<AthenaCourseConfigDTO>();
        const getSpy = vi
            .spyOn(athenaCourseConfigService, 'getCourseConfig')
            .mockReturnValueOnce(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }))
            .mockReturnValueOnce(response.asObservable());
        store.getOrCreate(5).ensureLoaded();

        const second = store.getOrCreate(5);
        second.ensureLoaded();

        expect(getSpy).toHaveBeenCalledTimes(2);
        expect(second.gradingFeedbackEnabled()).toBe(true);

        response.next({ gradingFeedbackEnabled: false, formativeFeedbackEnabled: true });

        expect(second.gradingFeedbackEnabled()).toBe(false);
        expect(second.formativeFeedbackEnabled()).toBe(true);
    });

    it('should drop every cached course when the user changes', () => {
        const before = store.getOrCreate(5);

        authenticationState.next({ id: 2 } as User);

        expect(store.getOrCreate(5)).not.toBe(before);
    });

    it('should drop every cached course on logout', () => {
        const before = store.getOrCreate(5);

        authenticationState.next(undefined);

        expect(store.getOrCreate(5)).not.toBe(before);
    });

    it('should keep the cached courses when the same user is reported again', () => {
        const before = store.getOrCreate(5);

        authenticationState.next({ id: 1 } as User);

        expect(store.getOrCreate(5)).toBe(before);
    });
});
