import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTableModule } from 'app/components/table/table.module';
import { triggerChanges } from '../../utils/general.utils';
import { TableEditableFieldComponent } from 'app/components/table/table-editable-field.component';

const expect = chai.expect;

describe('TableEditableFieldComponent', () => {
    let comp: TableEditableFieldComponent<any>;
    let fixture: ComponentFixture<TableEditableFieldComponent<any>>;
    let debugElement: DebugElement;

    const tableField = '.table-editable-field__value';
    const tableInputValue = '.table-editable-field__input';
    const tableFieldEdit = '.table-editable-field__edit';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisTableModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TableEditableFieldComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should render value as provided', () => {
        const value = 'test';

        comp.value = value;
        fixture.detectChanges();

        const tableEdit = debugElement.query(By.css(tableFieldEdit));
        expect(tableEdit).to.exist;

        const tableFieldValue = debugElement.query(By.css(tableField));

        expect(tableFieldValue).to.exist;
        expect(tableFieldValue.nativeElement.innerHTML).to.equal(value);
    });

    it('should show input when entering edit mode and fire update event on enter', fakeAsync(() => {
        const value = 'test';
        const fakeUpdateValue = { emit: jest.fn(() => {}) } as any;

        comp.value = value;
        comp.canEdit = true;
        comp.onValueUpdate = fakeUpdateValue;
        comp.isEditing = true;

        triggerChanges(comp, { property: 'isEditing', currentValue: true, previousValue: false, firstChange: false });
        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        const tableInput = debugElement.query(By.css(tableInputValue));
        expect(tableInput).to.exist;
        expect(tableInput.nativeElement.value).to.equal(value);

        tableInput.nativeElement.dispatchEvent(new Event('blur'));
        expect(fakeUpdateValue.emit.mock.calls.length).to.equal(1);
    }));

    it('should not fire enter editing event when not allowed to edit', fakeAsync(() => {
        const value = 'test';
        const fakeEnterEdit = { emit: jest.fn(() => {}) } as any;

        comp.value = value;
        comp.canEdit = false;
        comp.onEditStart = fakeEnterEdit as any;

        fixture.detectChanges();

        const tableEdit = debugElement.query(By.css(tableFieldEdit));
        expect(tableEdit).to.exist;
        expect(tableEdit.nativeElement.disabled).to.be.true;
        tableEdit.nativeElement.click();

        expect(fakeEnterEdit.emit.mock.calls.length).to.equal(0);
        const tableInput = debugElement.query(By.css(tableInputValue));
        expect(tableInput).not.to.exist;
    }));
});
