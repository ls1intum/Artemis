import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialRegistrationsImportModalTableComponent } from './tutorial-registrations-import-modal-table.component';

describe('TutorialRegistrationsImportModalTable', () => {
    let component: TutorialRegistrationsImportModalTableComponent;
    let fixture: ComponentFixture<TutorialRegistrationsImportModalTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsImportModalTableComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsImportModalTableComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
