import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';

describe('GenericConfirmationDialogComponent', () => {
    let component: GenericConfirmationDialogComponent;
    let fixture: ComponentFixture<GenericConfirmationDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [GenericConfirmationDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GenericConfirmationDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
