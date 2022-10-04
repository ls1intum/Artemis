import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupsRegisteredComponent } from './course-tutorial-groups-registered.component';

describe('CourseTutorialGroupsRegisteredComponent', () => {
    let component: CourseTutorialGroupsRegisteredComponent;
    let fixture: ComponentFixture<CourseTutorialGroupsRegisteredComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupsRegisteredComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupsRegisteredComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
