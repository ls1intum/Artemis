import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CoursePracticeComponent } from './course-practice.component';

describe('CoursePracticeComponent', () => {
    let component: CoursePracticeComponent;
    let fixture: ComponentFixture<CoursePracticeComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CoursePracticeComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CoursePracticeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
