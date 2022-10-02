import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';

describe('CourseTutorialGroupsComponent', () => {
    let component: CourseTutorialGroupsComponent;
    let fixture: ComponentFixture<CourseTutorialGroupsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
