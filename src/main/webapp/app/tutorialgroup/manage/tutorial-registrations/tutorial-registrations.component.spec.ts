import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsComponent } from './tutorial-registrations.component';

describe('RegisterStudents', () => {
    let component: TutorialRegistrationsComponent;
    let fixture: ComponentFixture<TutorialRegistrationsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
