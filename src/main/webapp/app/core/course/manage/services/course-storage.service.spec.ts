import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, distinctUntilChanged, firstValueFrom } from 'rxjs';

import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';

describe('CourseStorageService', () => {
    setupTestBed({ zoneless: true });

    let service: CourseStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(CourseStorageService);
    });

    it('should store and retrieve courses by id', () => {
        service.setCourses([{ id: 1, title: 'A' } as Course, { id: 2, title: 'B' } as Course]);
        expect(service.getCourse(1)?.title).toBe('A');
        expect(service.getCourse(2)?.title).toBe('B');
        expect(service.getCourse(3)).toBeUndefined();
    });

    it('should treat undefined input as an empty list', () => {
        service.setCourses(undefined);
        expect(service.getCourse(1)).toBeUndefined();
    });

    it('should replace an existing course on update and notify subscribers', async () => {
        service.setCourses([{ id: 1, title: 'old' } as Course]);
        const update$ = service.subscribeToCourseUpdates(1);
        const updated: Course = { id: 1, title: 'new' } as Course;
        const received = firstValueFrom(update$);
        service.updateCourse(updated);
        expect((await received).title).toBe('new');
        expect(service.getCourse(1)?.title).toBe('new');
    });

    it('should ignore updateCourse with no course argument', () => {
        service.setCourses([{ id: 1 } as Course]);
        service.updateCourse(undefined);
        expect(service.getCourse(1)).toBeDefined();
    });

    describe('authentication state changes', () => {
        let authState: BehaviorSubject<User | undefined>;
        let scoped: CourseStorageService;

        beforeEach(() => {
            authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
            const customAccountService = new MockAccountService();
            customAccountService.userIdentity.set({ id: 99 } as User);
            customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [{ provide: AccountService, useValue: customAccountService }],
            });
            scoped = TestBed.inject(CourseStorageService);
            scoped.setCourses([{ id: 1 } as Course]);
        });

        it('should clear stored courses on logout', () => {
            authState.next(undefined);
            expect(scoped.getCourse(1)).toBeUndefined();
        });

        it('should clear stored courses when a different user logs in', () => {
            authState.next({ id: 42 } as User);
            expect(scoped.getCourse(1)).toBeUndefined();
        });

        it('should complete existing subscriptions on logout', () => {
            let completed = false;
            scoped.subscribeToCourseUpdates(1).subscribe({ complete: () => (completed = true) });

            authState.next(undefined);

            expect(completed).toBe(true);
        });

        it('should not clear courses when the same user re-emits', () => {
            authState.next({ id: 99 } as User);
            expect(scoped.getCourse(1)).toBeDefined();
        });
    });
});
