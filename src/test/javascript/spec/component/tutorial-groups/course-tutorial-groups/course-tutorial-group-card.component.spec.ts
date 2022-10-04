import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupCardComponent } from 'app/overview/course-tutorial-groups/course-tutorial-group-card/course-tutorial-group-card.component';

describe('CourseTutorialGroupCardComponent', () => {
    let component: CourseTutorialGroupCardComponent;
    let fixture: ComponentFixture<CourseTutorialGroupCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupCardComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
