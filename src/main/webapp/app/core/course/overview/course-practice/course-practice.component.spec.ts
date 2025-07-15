import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CoursePracticeComponent } from './course-practice.component';
import { ActivatedRoute, Router } from '@angular/router';
import { MockBuilder } from 'ng-mocks';
import { of } from 'rxjs';

describe('CoursePracticeComponent', () => {
    let component: CoursePracticeComponent;
    let fixture: ComponentFixture<CoursePracticeComponent>;

    beforeEach(async () => {
        await MockBuilder(CoursePracticeComponent)
            .keep(Router)
            .provide({
                provide: ActivatedRoute,
                useValue: {
                    parent: {
                        params: of({ courseId: 1 }),
                    },
                },
            });

        fixture = TestBed.createComponent(CoursePracticeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should extract courseId from route params', () => {
        expect(component.courseId()).toBe(1);
    });

    it('should check for missing parent route', async () => {
        await MockBuilder(CoursePracticeComponent).keep(Router).provide({
            provide: ActivatedRoute,
            useValue: {},
        });

        const fixtureNoParent = TestBed.createComponent(CoursePracticeComponent);
        const componentNoParent = fixtureNoParent.componentInstance;
        fixtureNoParent.detectChanges();

        expect(componentNoParent.courseId()).toBeUndefined();
    });

    it('should navigate to quiz', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToPractice();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'training', 'quiz']);
    });
});
