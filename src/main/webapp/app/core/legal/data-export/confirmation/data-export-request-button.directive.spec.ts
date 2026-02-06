import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DataExportRequestButtonDirective } from 'app/core/legal/data-export/confirmation/data-export-request-button.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';

@Component({
    selector: 'jhi-test-component',
    template: '<button jhiDataExportRequestButton [adminDialog]="true" [expectedLogin]="\'login\'"></button>',
    imports: [DataExportRequestButtonDirective],
})
class TestComponent {}

describe('DataExportRequestButtonDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;
    let translateService: TranslateService;
    let translateSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [TestComponent, FaIconComponent, MockComponent(DataExportConfirmationDialogComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        await TestBed.compileComponents();

        fixture = TestBed.createComponent(TestComponent);
        debugElement = fixture.debugElement;
        translateService = TestBed.inject(TranslateService);
        translateSpy = vi.spyOn(translateService, 'instant');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be correctly initialized', () => {
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledWith('artemisApp.dataExport.request');

        // Check that button was assigned with proper classes via host binding.
        const confirmButton = debugElement.query(By.css('.btn.btn-primary.btn-lg.me-1'));
        expect(confirmButton).not.toBeNull();

        // Check that button text span was added to the DOM.
        const buttonText = debugElement.query(By.css('.d-xl-inline'));
        expect(buttonText).not.toBeNull();
        expect(buttonText.nativeElement.textContent).toBe('artemisApp.dataExport.request');

        const directiveEl = debugElement.query(By.directive(DataExportRequestButtonDirective));
        expect(directiveEl).not.toBeNull();
        const directiveInstance = directiveEl.injector.get(DataExportRequestButtonDirective);
        expect(directiveInstance.expectedLogin()).toBe('login');
    });

    it('should on click open the dialog', async () => {
        fixture.detectChanges();
        const directiveEl = debugElement.query(By.directive(DataExportRequestButtonDirective));
        const directiveInstance = directiveEl.injector.get(DataExportRequestButtonDirective);
        expect(directiveInstance.dialogVisible()).toBe(false);

        directiveEl.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(directiveInstance.dialogVisible()).toBe(true);
    });
});
