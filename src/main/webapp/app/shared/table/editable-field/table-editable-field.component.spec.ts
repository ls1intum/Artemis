import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { TableEditableFieldComponent } from 'app/shared/table/editable-field/table-editable-field.component';

describe('TableEditableFieldComponent', () => {
    let comp: TableEditableFieldComponent;
    let fixture: ComponentFixture<TableEditableFieldComponent>;
    let debugElement: DebugElement;

    const tableInputValue = '.table-editable-field__input';

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();
        fixture = TestBed.createComponent(TableEditableFieldComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    it('should render value as provided', fakeAsync(() => {
        const value = 'test';

        comp.value = value;
        fixture.detectChanges();
        fixture.whenStable().then(() => {
            expect(comp.inputValue).toEqual(value);

            const tableInput = debugElement.query(By.css(tableInputValue));

            expect(tableInput).not.toBeNull();
            expect(tableInput.nativeElement.value).toEqual(value);
        });
    }));

    it('should show input and fire update event on enter', fakeAsync(() => {
        const value = 'test';
        const fakeUpdateValue = jest.fn(() => {});

        comp.value = value;
        comp.onValueUpdate = fakeUpdateValue;
        fixture.detectChanges();
        fixture.whenStable().then(() => {
            const tableInput = debugElement.query(By.css(tableInputValue));
            expect(tableInput).not.toBeNull();
            expect(tableInput.nativeElement.value).toEqual(value);

            tableInput.nativeElement.dispatchEvent(new Event('blur'));
            expect(fakeUpdateValue.mock.calls).toHaveLength(1);
        });
    }));
});
