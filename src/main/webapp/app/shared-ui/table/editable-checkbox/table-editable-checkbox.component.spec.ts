import { vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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

    it('should render checkbox with its state as the boolean value provided and send an update on change', async () => {
        const checkbox = debugElement.query(By.css(tableCheckbox));

        fixture.componentRef.setInput('value', true);
        const updateSpy = vi.spyOn(comp.onValueUpdate, 'emit');
        fixture.detectChanges();

        await fixture.whenStable();
        expect(checkbox).not.toBeNull();
        expect(checkbox.nativeElement.checked).toBeTruthy();

        checkbox.nativeElement.click();

        await fixture.whenStable();

        expect(comp.value()).toBeTruthy();
        expect(checkbox).not.toBeNull();
        expect(checkbox.nativeElement.checked).toBeFalsy();

        // Send one update value after click.
        expect(updateSpy).toHaveBeenCalledOnce();
    });
});
