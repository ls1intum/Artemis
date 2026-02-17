import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { CourseIrisComponent } from './course-iris.component';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { Course } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-course-chatbot',
    template: '',
    standalone: true,
})
class MockCourseChatbotComponent {
    readonly courseId = input<number>();
    readonly hasAvailableExercises = input(true);

    toggleChatHistory = vi.fn();
}

describe('CourseIrisComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseIrisComponent;
    let fixture: ComponentFixture<CourseIrisComponent>;
    let paramMapSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
    let courseStorageService: CourseStorageService;

    beforeEach(async () => {
        paramMapSubject = new BehaviorSubject(convertToParamMap({ courseId: '123' }));

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
            ],
        })
            .overrideComponent(CourseIrisComponent, {
                remove: { imports: [CourseChatbotComponent] },
                add: { imports: [MockCourseChatbotComponent] },
            })
            .compileComponents();

        courseStorageService = TestBed.inject(CourseStorageService);

        fixture = TestBed.createComponent(CourseIrisComponent);
        component = fixture.componentInstance;
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

    it('should return hasAvailableExercises as false when course has no exercises', async () => {
        const course = { id: 456, exercises: [] } as Course;
        courseStorageService.updateCourse(course);
        paramMapSubject.next(convertToParamMap({ courseId: '456' }));
        await fixture.whenStable();
        expect(component.hasAvailableExercises()).toBe(false);
    });

    it('should return hasAvailableExercises as true when course has exercises', async () => {
        const course = { id: 456, exercises: [{}] } as Course;
        courseStorageService.updateCourse(course);
        paramMapSubject.next(convertToParamMap({ courseId: '456' }));
        await fixture.whenStable();
        expect(component.hasAvailableExercises()).toBe(true);
    });

    it('should return hasAvailableExercises as true when courseId is undefined', async () => {
        paramMapSubject.next(convertToParamMap({}));
        await fixture.whenStable();
        expect(component.hasAvailableExercises()).toBe(true);
    });
});
