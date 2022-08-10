import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { TableEditableCheckboxComponent } from 'app/shared/table/table-editable-checkbox.component';

describe('TableEditableFieldComponent', () => {
    let comp: TableEditableCheckboxComponent;
    let fixture: ComponentFixture<TableEditableCheckboxComponent>;
    let debugElement: DebugElement;

    const tableCheckbox = '.table-editable-field__checkbox';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisTableModule],
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
        const fakeUpdateValue = { emit: jest.fn(() => {}) } as any;

        comp.value = true;
        comp.onValueUpdate = fakeUpdateValue;
        fixture.detectChanges();

        await fixture.whenStable;
        expect(checkbox).not.toBeNull();
        expect(checkbox.nativeElement.checked).toBeTrue();

        checkbox.nativeElement.click();

        await fixture.whenStable;

        expect(comp.value).toBeTrue();
        expect(checkbox).not.toBeNull();
        expect(checkbox.nativeElement.checked).toBeFalse();

        // Send one update value after click.
        expect(fakeUpdateValue.emit.mock.calls).toHaveLength(1);
    });
});
