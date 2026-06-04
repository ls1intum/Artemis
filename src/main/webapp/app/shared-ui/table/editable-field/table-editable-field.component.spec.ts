import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { TableEditableFieldComponent } from 'app/shared-ui/table/editable-field/table-editable-field.component';
import { vi } from 'vitest';

describe('TableEditableFieldComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: TableEditableFieldComponent;
    let fixture: ComponentFixture<TableEditableFieldComponent>;
    let debugElement: DebugElement;

    const tableInputValue = '.table-editable-field__input';

    beforeEach(() => {
        return TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TableEditableFieldComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should render value as provided', async () => {
        const value = 'test';

        fixture.componentRef.setInput('value', value);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.inputValue).toEqual(value);

        const tableInput = debugElement.query(By.css(tableInputValue));

        expect(tableInput).not.toBeNull();
        expect(tableInput.nativeElement.value).toEqual(value);
    });

    it('should show input and fire update event on enter', async () => {
        const value = 'test';
        const fakeUpdateValue = vi.fn(() => {});

        fixture.componentRef.setInput('value', value);
        fixture.componentRef.setInput('onValueUpdate', fakeUpdateValue);
        fixture.detectChanges();
        await fixture.whenStable();

        const tableInput = debugElement.query(By.css(tableInputValue));
        expect(tableInput).not.toBeNull();
        expect(tableInput.nativeElement.value).toEqual(value);

        tableInput.nativeElement.dispatchEvent(new Event('blur'));
        expect(fakeUpdateValue).toHaveBeenCalledOnce();
    });
});
