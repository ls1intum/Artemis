import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CoursePracticeQuizComponent } from './course-practice-quiz.component';

describe('CoursePracticeQuizComponent', () => {
    let component: CoursePracticeQuizComponent;
    let fixture: ComponentFixture<CoursePracticeQuizComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CoursePracticeQuizComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CoursePracticeQuizComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
