import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { CourseNotificationWebsocketService } from 'app/notification/course-notification/course-notification-websocket.service';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { CourseHomeComponent } from './course-home.component';
import { CourseHomeService } from './course-home.service';
import { CourseHome, CourseHomeGeneratedWelcome } from './course-home.model';
import { COURSE_HOME_SITUATIONS, createCourseHomeFixture } from './test/course-home.fixtures';
import en from '../../../../i18n/en/courseHome.json';
import de from '../../../../i18n/de/courseHome.json';

describe('CourseHomeComponent', () => {
    let fixture: ComponentFixture<CourseHomeComponent>;
    let loads: Subject<CourseHome>[];
    let reselections: Subject<void>;
    let notifications: Subject<{ courseId: number }>;
    const clock = { now: () => dayjs('2026-10-26T10:00:00Z') };
    let ai: Subject<CourseHomeGeneratedWelcome>;
    let route: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
    const user = signal<User | undefined>(undefined);
    const course = signal<Course | undefined>(undefined);
    const api = { load: vi.fn(), acknowledgeVisit: vi.fn(), generateWelcome: vi.fn(), teachingTeam: vi.fn() };
    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
    const render = async (home: CourseHome) => {
        loads.at(-1)!.next(home);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };
    beforeEach(async () => {
        loads = [];
        reselections = new Subject();
        notifications = new Subject();
        ai = new Subject();
        route = new BehaviorSubject(convertToParamMap({ courseId: '42' }));
        user.set({ id: 1, firstName: 'Alex', courseRoles: [{ courseId: 42, roles: ['STUDENT'] }], selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
        course.set({ id: 42 } as Course);
        api.load.mockImplementation(() => {
            const load = new Subject<CourseHome>();
            loads.push(load);
            return load;
        });
        api.acknowledgeVisit.mockReturnValue(of(undefined));
        api.generateWelcome.mockReturnValue(ai);
        api.teachingTeam.mockReturnValue(
            of({
                items: [
                    { id: 1, name: 'Ada Lovelace', role: 'INSTRUCTOR' },
                    { id: 3, name: 'Grace Hopper', role: 'EDITOR' },
                ],
                hasMore: false,
                page: 0,
            }),
        );
        await TestBed.configureTestingModule({
            imports: [CourseHomeComponent],
            providers: [
                provideRouter([]),
                { provide: ArtemisServerDateService, useValue: clock },
                { provide: CourseTabRefreshService, useValue: { reselections: () => reselections } },
                { provide: CourseNotificationWebsocketService, useValue: { websocketNotification$: notifications } },
                provideTranslateService(),
                { provide: ActivatedRoute, useValue: { parent: { paramMap: route }, pathFromRoot: [] } },
                { provide: AccountService, useValue: { userIdentity: user, isAtLeastTutorInCourse: () => false } },
                { provide: CourseStorageService, useValue: { currentCourse: course } },
                { provide: CourseHomeService, useValue: api },
            ],
        }).compileComponents();
        const translate = TestBed.inject(TranslateService);
        translate.setTranslation('en', en);
        translate.setTranslation('de', de);
        translate.use('en');
        fixture = TestBed.createComponent(CourseHomeComponent);
        fixture.detectChanges();
    });
    afterEach(() => {
        vi.useRealTimers();
        fixture.destroy();
        vi.clearAllMocks();
    });
    it.each(COURSE_HOME_SITUATIONS)('renders %s without opening a dialog or initializing chat', async (situation) => {
        await render(createCourseHomeFixture(situation));
        expect(text()).toContain('Software Engineering');
        expect(fixture.nativeElement.querySelector('[data-testid="home-welcome"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('dialog, p-dialog, jhi-course-chatbot')).toBeNull();
        expect(api.generateWelcome).not.toHaveBeenCalled();
        expect(api.acknowledgeVisit).toHaveBeenCalledExactlyOnceWith(42, '2026-10-26T10:00:00Z', 2);
    });
    it('shows unknown and known read state independently from answer state', async () => {
        const home = createCourseHomeFixture('unread-replies');
        home.questions.items!.push({ ...home.questions.items![0], postId: 22, state: 'RESOLVED', unreadReplies: undefined });
        await render(home);
        expect(text()).toContain('2 unread replies');
        expect(text()).toContain('Resolved');
        const links = fixture.nativeElement.querySelectorAll('[data-testid="home-questions"] a');
        expect(links[1].getAttribute('href')).toContain('conversationId=8&messageId=21&focusReplyId=23');
    });
    it('hides disabled sections and keeps failures local', async () => {
        const home = createCourseHomeFixture('minimal');
        home.sections.lectures = 'UNAVAILABLE';
        await render(home);
        expect(fixture.nativeElement.querySelector('[data-testid="home-questions"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="home-iris"]')).toBeNull();
        expect(text()).toContain('temporarily unavailable');
    });
    it('avoids a percentage for 0/0 available work', async () => {
        await render(createCourseHomeFixture('before'));
        expect(text()).toContain('No exercises are available to you yet');
        expect(fixture.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    });
    it('renders fallback first and waits for AI before acknowledging', async () => {
        user.update((u) => ({ ...u!, selectedLLMUsage: LLMSelectionDecision.LOCAL_AI }));
        fixture.detectChanges();
        const home = createCourseHomeFixture('first');
        await render(home);
        expect(text()).toContain('Make this course your own');
        expect(api.generateWelcome).toHaveBeenCalledExactlyOnceWith(42, 'first', 'en');
        expect(api.acknowledgeVisit).not.toHaveBeenCalled();
        ai.next({ contextFingerprint: 'first', source: 'AI', message: 'A thoughtful first attempt can reveal a useful question.' });
        ai.complete();
        fixture.detectChanges();
        expect(text()).toContain('A thoughtful first attempt');
        expect(text()).toContain('AI-generated encouragement');
        expect(api.acknowledgeVisit).toHaveBeenCalledOnce();
    });
    it('discards late AI after consent is revoked', async () => {
        user.update((u) => ({ ...u!, selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }));
        fixture.detectChanges();
        await render(createCourseHomeFixture('first'));
        user.update((u) => ({ ...u!, selectedLLMUsage: LLMSelectionDecision.NO_AI }));
        fixture.detectChanges();
        ai.next({ contextFingerprint: 'first', source: 'AI', message: 'Stale private response' });
        fixture.detectChanges();
        expect(text()).not.toContain('Stale private response');
        expect(fixture.nativeElement.querySelector('[data-testid="home-iris"]')).toBeNull();
    });
    it('clears state on a new account and ignores old course responses', async () => {
        const oldLoad = loads[0];
        user.set({ id: 2, courseRoles: [{ courseId: 42, roles: ['STUDENT'] }] } as User);
        fixture.detectChanges();
        oldLoad.next(createCourseHomeFixture('first'));
        fixture.detectChanges();
        expect(text()).not.toContain('Software Engineering');
        await render(createCourseHomeFixture('minimal'));
        expect(text()).toContain('Software Engineering');
    });
    it('cancels an in-place course change before a stale response arrives', async () => {
        const oldLoad = loads[0];
        route.next(convertToParamMap({ courseId: '77' }));
        fixture.detectChanges();
        oldLoad.next(createCourseHomeFixture('first'));
        fixture.detectChanges();
        expect(text()).not.toContain('Software Engineering');
        expect(api.load).toHaveBeenCalledTimes(1);
        user.update((u) => ({ ...u!, courseRoles: [{ courseId: 77, roles: ['STUDENT'] }] }));
        course.set({ id: 77 } as Course);
        fixture.detectChanges();
        expect(api.load).toHaveBeenLastCalledWith(77, undefined);
    });
    it('supports long German content and inline deduplicated team expansion', async () => {
        TestBed.inject(TranslateService).use('de');
        const home = createCourseHomeFixture('first');
        home.course.title = 'Softwareentwicklungspraktikum für verteilte und nebenläufige Anwendungssysteme';
        home.teachingTeam.items![0].name = 'Alexandra von der Softwareentwicklungsforschung';
        await render(home);
        expect(text()).toContain(home.course.title);
        expect(text()).toContain(home.teachingTeam.items![0].name);
        (fixture.nativeElement.querySelector('[data-testid="home-team-more"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(api.teachingTeam).toHaveBeenCalledWith(42, 0);
        expect(text()).toContain('Grace Hopper');
        expect(fixture.nativeElement.querySelectorAll('tumaet-ui-avatar').length).toBe(2);
    });
    it('refreshes on reselection without rotating or acknowledging another visit', async () => {
        const initial = createCourseHomeFixture('first');
        await render(initial);
        const body = fixture.nativeElement.querySelector('[data-testid="home-welcome-body"]').textContent;
        reselections.next();
        expect(api.load).toHaveBeenLastCalledWith(42, initial.visit.newSince);
        const refreshed = createCourseHomeFixture('first');
        refreshed.visit.firstVisit = false;
        refreshed.welcome.variantIndex = 0;
        await render(refreshed);
        expect(fixture.nativeElement.querySelector('[data-testid="home-welcome-body"]').textContent).toBe(body);
        expect(text()).toContain('How we learn in this course');
        expect(api.acknowledgeVisit).toHaveBeenCalledOnce();
        expect(api.generateWelcome).not.toHaveBeenCalled();
    });
    it.each([401, 403, 404])('clears protected content and pending requests after a denied refresh (%s)', async (status) => {
        user.update((value) => ({ ...value!, selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }));
        fixture.detectChanges();
        await render(createCourseHomeFixture('first'));
        reselections.next();
        loads.at(-1)!.error(new HttpErrorResponse({ status }));
        fixture.detectChanges();
        expect(fixture.componentInstance.home()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('[data-testid="home-team"]')).toBeNull();
        expect(text()).not.toContain('Software Engineering');
        expect(text()).toContain('could not be loaded');
        ai.next({ contextFingerprint: 'first', source: 'AI', message: 'Late protected wording' });
        fixture.detectChanges();
        expect(text()).not.toContain('Late protected wording');
    });
    it.each(['welcome', 'team'])('clears protected content when the %s endpoint reports lost access', async (source) => {
        user.update((value) => ({ ...value!, selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }));
        fixture.detectChanges();
        await render(createCourseHomeFixture('first'));
        const staff = new Subject<never>();
        if (source === 'team') {
            api.teachingTeam.mockReturnValue(staff);
            fixture.componentInstance.loadMoreTeam();
            staff.error(new HttpErrorResponse({ status: 403 }));
        } else {
            ai.error(new HttpErrorResponse({ status: 403 }));
        }
        fixture.detectChanges();
        expect(fixture.componentInstance.home()).toBeUndefined();
        expect(text()).not.toContain('Software Engineering');
        expect(text()).toContain('could not be loaded');
    });
    it('keeps the rendered snapshot after a transient refresh failure', async () => {
        await render(createCourseHomeFixture('unread-replies'));
        reselections.next();
        loads.at(-1)!.error(new HttpErrorResponse({ status: 503 }));
        fixture.detectChanges();
        expect(text()).toContain('Software Engineering');
        expect(text()).toContain('2 unread replies');
        expect(text()).toContain('Updates could not be loaded');
    });
    it.each([false, true])('adopts revoked server eligibility despite an unchanged fingerprint (already generated: %s)', async (generated) => {
        user.update((value) => ({ ...value!, selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }));
        fixture.detectChanges();
        await render(createCourseHomeFixture('first'));
        if (generated) {
            ai.next({ contextFingerprint: 'first', source: 'AI', message: 'Previously generated wording' });
            ai.complete();
        }
        const fresh = createCourseHomeFixture('first');
        fresh.welcome.aiEligible = false;
        fresh.welcome.variantIndex = 0;
        fresh.visit.firstVisit = false;
        reselections.next();
        await render(fresh);
        expect(fixture.componentInstance.home()!.welcome.aiEligible).toBe(false);
        expect(fixture.componentInstance.home()!.welcome.variantIndex).toBe(2);
        expect(fixture.componentInstance.home()!.visit.firstVisit).toBe(true);
        ai.next({ contextFingerprint: 'first', source: 'AI', message: 'Late generated wording' });
        fixture.detectChanges();
        expect(text()).not.toContain('AI-generated encouragement');
        expect(text()).toContain('Make this course your own');
    });
    it('coalesces current-course notifications and cancels a pending burst on destroy', async () => {
        await render(createCourseHomeFixture('minimal'));
        vi.useFakeTimers();
        notifications.next({ courseId: 77 });
        await vi.advanceTimersByTimeAsync(300);
        expect(api.load).toHaveBeenCalledTimes(1);
        notifications.next({ courseId: 42 });
        notifications.next({ courseId: 42 });
        await vi.advanceTimersByTimeAsync(299);
        expect(api.load).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(api.load).toHaveBeenCalledTimes(2);
        notifications.next({ courseId: 42 });
        fixture.destroy();
        await vi.advanceTimersByTimeAsync(300);
        expect(api.load).toHaveBeenCalledTimes(2);
    });
    it('refreshes the selected action at a server-time boundary', async () => {
        const home = createCourseHomeFixture('due-today');
        home.nextRefreshAt = '2026-10-26T10:00:01Z';
        vi.useFakeTimers();
        loads.at(-1)!.next(home);
        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(1100);
        expect(api.load).toHaveBeenCalledTimes(2);
        expect(api.load).toHaveBeenLastCalledWith(42, home.visit.newSince);
    });
    it('refreshes when the document becomes visible', async () => {
        await render(createCourseHomeFixture('minimal'));
        document.dispatchEvent(new Event('visibilitychange'));
        expect(api.load).toHaveBeenCalledTimes(2);
    });
    it('retains deterministic text on AI failure and retries a failed core load', async () => {
        loads[0].error(new Error('unavailable'));
        fixture.detectChanges();
        expect(text()).toContain('could not be loaded');
        fixture.nativeElement.querySelector('button').click();
        user.update((u) => ({ ...u!, selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }));
        fixture.detectChanges();
        await render(createCourseHomeFixture('first'));
        ai.error(new Error('provider down'));
        fixture.detectChanges();
        expect(text()).toContain('Make this course your own');
        expect(text()).not.toContain('AI-generated encouragement');
        expect(api.acknowledgeVisit).toHaveBeenCalledOnce();
    });
    it('gives an untitled Iris next action an accessible localized link name', async () => {
        const home = createCourseHomeFixture('iris-follow-up');
        home.nextStep!.title = '';
        await render(home);
        expect(fixture.nativeElement.querySelector('[data-testid="home-next-step"] a').textContent.trim()).toBe('Course conversation');
    });
    it('opens an authorized course immediately after enrollment with a stale account role cache', async () => {
        fixture.destroy();
        api.load.mockClear();
        loads = [];
        user.update((value) => ({ ...value!, courseRoles: [] }));
        fixture = TestBed.createComponent(CourseHomeComponent);
        fixture.detectChanges();
        expect(api.load).toHaveBeenCalledExactlyOnceWith(42, undefined);
        await render(createCourseHomeFixture('first'));
        expect(text()).toContain('Software Engineering');
    });
});
