import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { CourseIrisComponent } from './course-iris.component';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockProvider } from 'ng-mocks';

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
    let llmOptedOutSubject: Subject<void>;
    let router: Router;

    beforeEach(async () => {
        paramMapSubject = new BehaviorSubject(convertToParamMap({ courseId: '123' }));
        llmOptedOutSubject = new Subject<void>();

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
                MockProvider(IrisChatService, {
                    llmOptedOut$: llmOptedOutSubject.asObservable(),
                }),
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

    it('should navigate to exercises when the user opts out of AI via the chat service', async () => {
        await fixture.whenStable();

        llmOptedOutSubject.next();

        expect(router.navigate).toHaveBeenCalledWith(['/courses', 123, 'exercises']);
    });

    it('should not navigate if the course id is not resolved yet when the opt-out event fires', async () => {
        paramMapSubject.next(convertToParamMap({}));
        await fixture.whenStable();

        llmOptedOutSubject.next();

        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not navigate on opt-out after the component is destroyed', async () => {
        await fixture.whenStable();
        fixture.destroy();

        llmOptedOutSubject.next();

        expect(router.navigate).not.toHaveBeenCalled();
    });
});
