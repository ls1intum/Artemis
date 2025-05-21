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
                    parent: { params: of({ courseId: 1 }) },
                },
            });

        fixture = TestBed.createComponent(CoursePracticeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initiate', () => {
        expect(component.courseId).toBe(1);
    });

    it('should navigate to quiz', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToPractice();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'quiz']);
    });
});
