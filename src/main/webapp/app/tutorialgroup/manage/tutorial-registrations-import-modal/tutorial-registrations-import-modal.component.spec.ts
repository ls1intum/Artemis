import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsImportModalComponent } from './tutorial-registrations-import-modal.component';

describe('TutorialRegistrationsImportModal', () => {
    let component: TutorialRegistrationsImportModalComponent;
    let fixture: ComponentFixture<TutorialRegistrationsImportModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsImportModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsImportModalComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
