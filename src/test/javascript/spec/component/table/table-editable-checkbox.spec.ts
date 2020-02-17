import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTableModule } from 'app/components/table/table.module';
import { TableEditableCheckboxComponent } from 'app/components/table/table-editable-checkbox.component';

const expect = chai.expect;

describe('TableEditableFieldComponent', () => {
    let comp: TableEditableCheckboxComponent;
    let fixture: ComponentFixture<TableEditableCheckboxComponent>;
    let debugElement: DebugElement;

    const tableCheckbox = '.table-editable-field__checkbox';

    beforeEach(async () => {
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
        expect(checkbox).to.exist;
        expect(checkbox.nativeElement.checked).to.be.true;

        checkbox.nativeElement.click();

        await fixture.whenStable;

        expect(comp.value).to.be.true;
        expect(checkbox).to.exist;
        expect(checkbox.nativeElement.checked).to.be.false;

        // Send one update value after click.
        expect(fakeUpdateValue.emit.mock.calls.length).to.equal(1);
    });
});
