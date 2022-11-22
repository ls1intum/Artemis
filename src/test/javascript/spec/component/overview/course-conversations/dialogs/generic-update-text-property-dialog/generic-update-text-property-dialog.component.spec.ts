import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GenericUpdateTextPropertyDialogComponent } from 'app/overview/course-conversations/dialogs/generic-update-text-property-dialog/generic-update-text-property-dialog.component';

describe('GenericUpdateTextPropertyDialog', () => {
    let component: GenericUpdateTextPropertyDialogComponent;
    let fixture: ComponentFixture<GenericUpdateTextPropertyDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [GenericUpdateTextPropertyDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GenericUpdateTextPropertyDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
