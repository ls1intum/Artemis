import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsRegisterModalComponent } from './tutorial-registrations-register-modal.component';

describe('TutorialRegistrationsRegisterModal', () => {
    let component: TutorialRegistrationsRegisterModalComponent;
    let fixture: ComponentFixture<TutorialRegistrationsRegisterModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsRegisterModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsRegisterModalComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
