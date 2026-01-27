import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsContainerComponent } from './tutorial-registrations-container.component';

describe('TutorialRegistrationsContainer', () => {
    let component: TutorialRegistrationsContainerComponent;
    let fixture: ComponentFixture<TutorialRegistrationsContainerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsContainerComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsContainerComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
