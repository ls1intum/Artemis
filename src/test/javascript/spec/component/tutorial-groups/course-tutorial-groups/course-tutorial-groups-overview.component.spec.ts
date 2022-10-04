import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupsOverviewComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups-overview/course-tutorial-groups-overview.component';

describe('CourseTutorialGroupsOverviewComponent', () => {
    let component: CourseTutorialGroupsOverviewComponent;
    let fixture: ComponentFixture<CourseTutorialGroupsOverviewComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupsOverviewComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupsOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
