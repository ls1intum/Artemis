import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiCourseCardComponent } from 'app/lti/lti-course-card.component';

describe('LtiCourseCardComponent', () => {
    let component: LtiCourseCardComponent;
    let fixture: ComponentFixture<LtiCourseCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [LtiCourseCardComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LtiCourseCardComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set default color if no color is specified in course', () => {
        component.course = { id: 1, shortName: 'lti-course', title: 'LTI COURSE' };
        component.ngOnChanges();
        expect(component.courseColor).toBe(component.ARTEMIS_DEFAULT_COLOR);
    });

    it('should use course color if specified', () => {
        const testColor = '#123456';
        component.course = { id: 1, shortName: 'lti-course', title: 'LTI COURSE', color: testColor };
        component.ngOnChanges();
        expect(component.courseColor).toBe(testColor);
    });
});
