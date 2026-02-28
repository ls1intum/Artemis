import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsRegisterSearchBarComponent } from './tutorial-registrations-register-search-bar.component';

describe('TutorialRegistrationsRegisterSearchBar', () => {
    let component: TutorialRegistrationsRegisterSearchBarComponent;
    let fixture: ComponentFixture<TutorialRegistrationsRegisterSearchBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsRegisterSearchBarComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsRegisterSearchBarComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
