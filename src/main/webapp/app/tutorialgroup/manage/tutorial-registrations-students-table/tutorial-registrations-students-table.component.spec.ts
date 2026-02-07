import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsStudentsTableComponent } from './tutorial-registrations-students-table.component';

describe('TutorialRegistrationsStudentsTable', () => {
    let component: TutorialRegistrationsStudentsTableComponent;
    let fixture: ComponentFixture<TutorialRegistrationsStudentsTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsStudentsTableComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsStudentsTableComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
