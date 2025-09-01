import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupDetailSessionStatusChipComponent } from './course-tutorial-group-detail-session-status-chip.component';

describe('TutorialGroupDetailSessionStatusChip', () => {
    let component: CourseTutorialGroupDetailSessionStatusChipComponent;
    let fixture: ComponentFixture<CourseTutorialGroupDetailSessionStatusChipComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseTutorialGroupDetailSessionStatusChipComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupDetailSessionStatusChipComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
