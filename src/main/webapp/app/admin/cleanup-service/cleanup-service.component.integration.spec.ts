import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
import { DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { TumUiDatePickerComponent } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CleanupServiceComponent date range integration', () => {
    let fixture: ComponentFixture<CleanupServiceComponent>;
    let component: CleanupServiceComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CleanupServiceComponent],
            providers: [
                {
                    provide: DataCleanupService,
                    useValue: {
                        getLastExecutions: vi.fn().mockReturnValue(of(new HttpResponse({ body: [] }))),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CleanupServiceComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    function datePickers(operationName: string): TumUiDatePickerComponent[] {
        const row = fixture.debugElement.query(By.css(`[data-testid="cleanup-row-${operationName}"]`));
        return row.queryAll(By.directive(TumUiDatePickerComponent)).map((debugElement) => debugElement.componentInstance as TumUiDatePickerComponent);
    }

    it('should recover when the to-date corrects an invalid date range', () => {
        const operation = component.cleanupOperations()[1];
        const [fromPicker, toPicker] = datePickers(operation.name);
        const invalidFrom = operation.deleteTo!.add(1, 'day');

        // Writing the picker's value model emits its valueChange (the model output) exactly as user input would,
        // so the template's (valueChange)="onDeleteFromChange(operation, $event)" handler runs with the new date.
        fromPicker.value.set(invalidFrom);
        fixture.detectChanges();

        expect(operation.datesValid()).toBe(false);

        const correctedTo = invalidFrom.add(1, 'day');
        toPicker.value.set(correctedTo);
        fixture.detectChanges();

        expect(operation.deleteFrom?.toISOString()).toBe(invalidFrom.toISOString());
        expect(operation.deleteTo?.toISOString()).toBe(correctedTo.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('should recover when the from-date corrects an invalid date range', () => {
        const operation = component.cleanupOperations()[1];
        const [fromPicker, toPicker] = datePickers(operation.name);
        const invalidTo = operation.deleteFrom!.subtract(1, 'day');

        toPicker.value.set(invalidTo);
        fixture.detectChanges();

        expect(operation.datesValid()).toBe(false);

        const correctedFrom = invalidTo.subtract(1, 'day');
        fromPicker.value.set(correctedFrom);
        fixture.detectChanges();

        expect(operation.deleteFrom?.toISOString()).toBe(correctedFrom.toISOString());
        expect(operation.deleteTo?.toISOString()).toBe(invalidTo.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('disables the destructive Execute button when a date field is overwritten with unparseable text', () => {
        const operation = component.cleanupOperations()[1];
        const row = fixture.debugElement.query(By.css(`[data-testid="cleanup-row-${operation.name}"]`));
        const executeButton = () => row.query(By.css('[data-testid="execute-operation"]')).nativeElement as HTMLButtonElement;

        // Valid seeded range → Execute enabled.
        expect(executeButton().disabled).toBe(false);

        // Overwrite the "from" field with garbage. This is the keepInvalid path: the picker does NOT emit
        // valueChange (so operation.deleteFrom keeps its stale valid date and datesValid stays true), but it
        // DOES emit parseValidChange(false). The Execute button must reflect that and disable — otherwise the admin
        // could run the destructive cleanup against a stale range while the field shows unparseable text.
        const fromInput = row.query(By.css('[data-testid="delete-from-picker"] [data-testid="tum-ui-date-picker-input"]')).nativeElement as HTMLInputElement;
        fromInput.value = 'not a date';
        fromInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(operation.datesValid()).toBe(true); // value unchanged (keepInvalid), so the range check still passes
        expect(operation.deleteFromValid()).toBe(false); // but the typed text no longer parses
        expect(executeButton().disabled).toBe(true);
    });
});
