import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { CourseIrisComponent } from './course-iris.component';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-course-chatbot',
    template: '',
    standalone: true,
})
class MockCourseChatbotComponent {
    readonly courseId = input<number>();

    toggleChatHistory = vi.fn();
}

describe('CourseIrisComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseIrisComponent;
    let fixture: ComponentFixture<CourseIrisComponent>;
    let paramMapSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
    let userIdentitySignal: ReturnType<typeof signal<User | undefined>>;
    let router: Router;

    const setUserSelection = (decision: LLMSelectionDecision | undefined) => {
        // Mirror the production immutable update so toObservable emits.
        userIdentitySignal.set(Object.assign({}, userIdentitySignal() ?? ({} as User), { selectedLLMUsage: decision }) as User);
    };

    beforeEach(async () => {
        paramMapSubject = new BehaviorSubject(convertToParamMap({ courseId: '123' }));
        userIdentitySignal = signal<User | undefined>(undefined);

        await TestBed.configureTestingModule({
            imports: [CourseIrisComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            paramMap: paramMapSubject.asObservable(),
                        },
                    },
                },
                {
                    provide: AccountService,
                    useValue: { userIdentity: userIdentitySignal },
                },
            ],
        })
            .overrideComponent(CourseIrisComponent, {
                remove: { imports: [CourseChatbotComponent] },
                add: { imports: [MockCourseChatbotComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseIrisComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockResolvedValue(true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should parse courseId from route params', async () => {
        await fixture.whenStable();
        expect(component.courseId()).toBe(123);
    });

    it('should return undefined for invalid courseId', async () => {
        paramMapSubject.next(convertToParamMap({ courseId: 'invalid' }));
        await fixture.whenStable();
        expect(component.courseId()).toBeUndefined();
    });

    it('should return undefined when courseId is missing', async () => {
        paramMapSubject.next(convertToParamMap({}));
        await fixture.whenStable();
        expect(component.courseId()).toBeUndefined();
    });

    it('should toggle isCollapsed when toggleSidebar is called', () => {
        expect(component.isCollapsed).toBe(false);

        component.toggleSidebar();
        expect(component.isCollapsed).toBe(true);

        component.toggleSidebar();
        expect(component.isCollapsed).toBe(false);
    });

    it('should initialize isCollapsed to false', () => {
        expect(component.isCollapsed).toBe(false);
    });

    it('should navigate to exercises when the user transitions from a non-NO_AI selection to NO_AI', async () => {
        setUserSelection(LLMSelectionDecision.LOCAL_AI);
        await fixture.whenStable();
        expect(router.navigate).not.toHaveBeenCalled();

        setUserSelection(LLMSelectionDecision.NO_AI);
        await fixture.whenStable();

        expect(router.navigate).toHaveBeenCalledWith(['/courses', 123, 'exercises']);
    });

    it('should NOT navigate on initial async resolution into LOCAL_AI', async () => {
        // userIdentity starts undefined; auth resolves and emits LOCAL_AI. Not a NO_AI transition.
        setUserSelection(LLMSelectionDecision.LOCAL_AI);
        await fixture.whenStable();

        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should navigate on first-time opt-out from undefined to NO_AI', async () => {
        // The user logs in with no decision (selectedLLMUsage=undefined), the LLM modal opens,
        // they pick NO_AI — pairwise sees [undefined, NO_AI]. This is a real opt-out and MUST redirect,
        // otherwise the user is stuck on the now-useless /iris page.
        setUserSelection(LLMSelectionDecision.NO_AI);
        await fixture.whenStable();

        expect(router.navigate).toHaveBeenCalledWith(['/courses', 123, 'exercises']);
    });

    it('should navigate when component mounts with userIdentity already at NO_AI', async () => {
        // Cached-auth case: in-app navigation lands on /iris while the signal already holds
        // NO_AI. pairwise alone would need two real emissions to fire — startWith(undefined) is
        // what makes this case redirect.
        userIdentitySignal.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);

        // Re-create the fixture so the constructor's subscription captures the seeded value.
        fixture.destroy();
        fixture = TestBed.createComponent(CourseIrisComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockResolvedValue(true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(router.navigate).toHaveBeenCalledWith(['/courses', 123, 'exercises']);
    });

    it('should not navigate if the course id is not resolved yet when NO_AI fires', async () => {
        paramMapSubject.next(convertToParamMap({}));
        await fixture.whenStable();

        setUserSelection(LLMSelectionDecision.LOCAL_AI);
        await fixture.whenStable();
        setUserSelection(LLMSelectionDecision.NO_AI);
        await fixture.whenStable();

        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not navigate after the component is destroyed', async () => {
        setUserSelection(LLMSelectionDecision.LOCAL_AI);
        await fixture.whenStable();
        fixture.destroy();

        setUserSelection(LLMSelectionDecision.NO_AI);

        expect(router.navigate).not.toHaveBeenCalled();
    });
});
