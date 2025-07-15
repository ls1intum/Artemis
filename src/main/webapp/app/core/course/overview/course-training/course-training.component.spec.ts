import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTrainingComponent } from './course-training.component';
import { ActivatedRoute, Router } from '@angular/router';
import { MockBuilder } from 'ng-mocks';
import { of } from 'rxjs';

describe('CourseTrainingComponent', () => {
    let component: CourseTrainingComponent;
    let fixture: ComponentFixture<CourseTrainingComponent>;

    beforeEach(async () => {
        await MockBuilder(CourseTrainingComponent)
            .keep(Router)
            .provide({
                provide: ActivatedRoute,
                useValue: {
                    parent: {
                        params: of({ courseId: 1 }),
                    },
                },
            });

        fixture = TestBed.createComponent(CourseTrainingComponent);
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
        await MockBuilder(CourseTrainingComponent).keep(Router).provide({
            provide: ActivatedRoute,
            useValue: {},
        });

        const fixtureNoParent = TestBed.createComponent(CourseTrainingComponent);
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
