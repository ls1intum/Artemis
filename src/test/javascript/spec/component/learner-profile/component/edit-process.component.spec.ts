import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditProcessComponent, EditStateTransition } from 'app/shared/editable-slider/edit-process.component';

describe('EditProcessComponent', () => {
    let component: EditProcessComponent;
    let fixture: ComponentFixture<EditProcessComponent>;

    const editStateTransition = signal(EditStateTransition.Abort);

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();

        fixture = TestBed.createComponent(EditProcessComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('editStateTransition', editStateTransition);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.editStateTransition()).toEqual(editStateTransition);
        expect(component.disabled()).toBeFalsy();
    });

    it('should change state on edit', async () => {
        component.onEdit();
        fixture.autoDetectChanges();
        await fixture.whenStable();
        expect(component.editStateTransition()).toEqual(EditStateTransition.Edit);
    });

    it('should change state on abort', async () => {
        component.onAbort();
        fixture.autoDetectChanges();
        await fixture.whenStable();
        expect(component.editStateTransition()).toEqual(EditStateTransition.Abort);
    });

    it('should change state on save', async () => {
        component.onSave();
        fixture.autoDetectChanges();
        await fixture.whenStable();
        expect(component.editStateTransition()).toEqual(EditStateTransition.TrySave);
    });
});
