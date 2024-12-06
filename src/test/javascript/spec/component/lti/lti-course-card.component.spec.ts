import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiCourseCardComponent } from 'app/lti/lti-course-card.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

describe('LtiCourseCardComponent', () => {
    let component: LtiCourseCardComponent;
    let fixture: ComponentFixture<LtiCourseCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LtiCourseCardComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({}),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LtiCourseCardComponent);
        fixture.componentRef.setInput('course', { id: 1, shortName: 'lti-course', title: 'LTI COURSE' });
        component = fixture.componentInstance;
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should set default color if no color is specified in course', () => {
        fixture.detectChanges();
        expect(component.courseColor).toBe(ARTEMIS_DEFAULT_COLOR);
    });

    it('should use course color if specified', () => {
        const testColor = '#123456';
        fixture.componentRef.setInput('course', { id: 1, shortName: 'lti-course', title: 'LTI COURSE', color: testColor });
        fixture.detectChanges();
        expect(component.courseColor).toBe(testColor);
    });
});
