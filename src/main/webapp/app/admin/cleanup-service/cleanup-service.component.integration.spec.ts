import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
import { DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { TumUiDatePickerComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-date-picker.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CleanupServiceComponent date range integration', () => {
    setupTestBed({ zoneless: true });

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
        // and resets hasValidInput() to true, so the template's (valueChange) handler runs with the new date.
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
});
