import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { CourseSettingCategoryDirective } from 'app/core/course/overview/course-settings/directive/course-setting-category.directive';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { SubjectObservablePair } from 'app/shared/util/rxjs.utils';

// Create a concrete test component that extends the directive
@Component({
    template: '',
    selector: 'jhi-test-component',
    standalone: true,
})
class TestComponent extends CourseSettingCategoryDirective {
    onCourseAvailableCalled = 0;
    onCourseIdAvailableCalled = 0;

    onCourseAvailable(): void {
        this.onCourseAvailableCalled++;
    }

    onCourseIdAvailable(): void {
        this.onCourseIdAvailableCalled++;
    }
}

describe('CourseSettingCategoryDirective', () => {
    setupTestBed({ zoneless: true });

    let component: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let courseStorageServiceMock: {
        getCourse: ReturnType<typeof vi.fn>;
        subscribeToCourseUpdates: ReturnType<typeof vi.fn>;
    };
    let activatedRouteMock: Partial<ActivatedRoute>;
    let parentRouteParamsSubject: BehaviorSubject<{ courseId: string }>;
    let courseUpdatesSubject: Subject<Course>;

    const courseId = 123;
    const mockCourse: Course = { id: courseId, title: 'Test Course' } as Course;

    beforeEach(() => {
        parentRouteParamsSubject = new BehaviorSubject<{ courseId: string }>({ courseId: courseId.toString() });

        courseUpdatesSubject = new Subject<Course>();
        const courseUpdatesPair = new SubjectObservablePair<Course>();
        courseUpdatesPair.subject = courseUpdatesSubject;

        courseStorageServiceMock = {
            getCourse: vi.fn().mockReturnValue(mockCourse),
            subscribeToCourseUpdates: vi.fn().mockReturnValue(courseUpdatesPair.observable),
        };

        activatedRouteMock = {
            // @ts-ignore
            parent: {
                params: parentRouteParamsSubject.asObservable(),
            },
        };

        TestBed.configureTestingModule({
            imports: [TestComponent],
            providers: [
                { provide: CourseStorageService, useValue: courseStorageServiceMock },
                { provide: ActivatedRoute, useValue: activatedRouteMock },
            ],
        });

        fixture = TestBed.createComponent(TestComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create an instance', () => {
        expect(component).toBeTruthy();
    });

    it('should set courseId from route params and call onCourseIdAvailable', () => {
        fixture.detectChanges();

        expect(component.courseId).toBe(courseId);
        expect(component.onCourseIdAvailableCalled).toBe(1);
    });

    it('should get course from storage service on init', () => {
        fixture.detectChanges();

        expect(courseStorageServiceMock.getCourse).toHaveBeenCalledWith(courseId);
        expect(component.course).toEqual(mockCourse);
    });

    it('should subscribe to course updates', () => {
        fixture.detectChanges();

        expect(courseStorageServiceMock.subscribeToCourseUpdates).toHaveBeenCalledWith(courseId);
    });

    it('should unsubscribe from observables when destroyed', () => {
        fixture.detectChanges();

        const parentParamUnsubscribeSpy = vi.spyOn((component as any).parentParamSubscription, 'unsubscribe');
        const courseUpdatesUnsubscribeSpy = vi.spyOn((component as any).courseUpdatesSubscription, 'unsubscribe');

        fixture.destroy();

        expect(parentParamUnsubscribeSpy).toHaveBeenCalledOnce();
        expect(courseUpdatesUnsubscribeSpy).toHaveBeenCalledOnce();
    });

    it('should handle undefined parent route gracefully', () => {
        const noParentRouteMock = {
            parent: undefined,
        };

        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            imports: [TestComponent],
            providers: [
                { provide: CourseStorageService, useValue: courseStorageServiceMock },
                { provide: ActivatedRoute, useValue: noParentRouteMock },
            ],
        });

        const newFixture = TestBed.createComponent(TestComponent);
        const newComponent = newFixture.componentInstance;

        expect(() => {
            newFixture.detectChanges();
        }).not.toThrow();

        expect(newComponent.onCourseIdAvailableCalled).toBe(0);
    });

    it('should handle route parameter changes', () => {
        fixture.detectChanges();

        expect(component.courseId).toBe(courseId);

        const newCourseId = 456;
        parentRouteParamsSubject.next({ courseId: newCourseId.toString() });

        expect(component.courseId).toBe(newCourseId);
        expect(component.onCourseIdAvailableCalled).toBe(2);
    });

    it('should handle null course from storage service', () => {
        courseStorageServiceMock.getCourse.mockReturnValue(undefined);

        fixture.detectChanges();

        expect(component.course).toBeUndefined();
        expect(courseStorageServiceMock.subscribeToCourseUpdates).toHaveBeenCalledWith(courseId);
    });
});
