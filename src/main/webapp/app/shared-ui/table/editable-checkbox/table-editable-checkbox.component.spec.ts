import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import { TableEditableCheckboxComponent } from 'app/shared-ui/table/editable-checkbox/table-editable-checkbox.component';

describe('TableEditableFieldComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TableEditableCheckboxComponent;
    let fixture: ComponentFixture<TableEditableCheckboxComponent>;
    let debugElement: DebugElement;

    const tableCheckbox = '.table-editable-field__checkbox';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TableEditableCheckboxComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render checkbox with its state as the boolean value provided and send an update on change', async () => {
        const updateSpy = vi.fn(() => {});
        comp.onValueUpdate.subscribe(updateSpy);

        fixture.componentRef.setInput('value', true);
        fixture.detectChanges();
        await fixture.whenStable();

        const checkbox = debugElement.query(By.css(tableCheckbox));
        expect(checkbox).not.toBeNull();
        expect(checkbox.nativeElement.checked).toBe(true);

        checkbox.nativeElement.click();

        await fixture.whenStable();

        // The input is one-way bound, so the (unchanged) value signal still reflects the provided input.
        expect(comp.value()).toBe(true);
        expect(checkbox).not.toBeNull();
        expect(checkbox.nativeElement.checked).toBe(false);

        // Send one update value after click.
        expect(updateSpy.mock.calls).toHaveLength(1);
    });
});
