import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
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
        registerLocaleData(localeDe);
        loads = [];
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
});
