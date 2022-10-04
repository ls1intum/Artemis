import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupDetailComponent } from 'app/overview/tutorial-group-details/course-tutorial-group-detail/course-tutorial-group-detail.component';

describe('CourseTutorialGroupDetailComponent', () => {
    let component: CourseTutorialGroupDetailComponent;
    let fixture: ComponentFixture<CourseTutorialGroupDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupDetailComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
