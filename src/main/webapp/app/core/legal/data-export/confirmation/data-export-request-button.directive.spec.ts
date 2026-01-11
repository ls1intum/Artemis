import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Component, DebugElement, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DataExportRequestButtonDirective } from 'app/core/legal/data-export/confirmation/data-export-request-button.directive';
import { DataExportConfirmationDialogService } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-test-component',
    template: '<button jhiDataExportRequestButton [adminDialog]="true" [expectedLogin]="\'login\'"></button>',
    imports: [DataExportRequestButtonDirective],
})
class TestComponent {}

describe('DataExportRequestButtonDirective', () => {
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;
    let dataExportConfirmationDialogService: DataExportConfirmationDialogService;
    let translateService: TranslateService;
    let translateSpy: jest.SpyInstance;

    beforeEach(() => {
        // Create mock componentInstance with signal-like properties
        const mockComponentInstance = {
            expectedLogin: signal(''),
            adminDialog: signal(false),
            expectedLoginOfOtherUser: signal(''),
            dataExportRequest: undefined,
            dataExportRequestForAnotherUser: undefined,
            dialogError: undefined,
        };

        const mockModalService = {
            open: jest.fn().mockReturnValue({
                result: Promise.resolve(),
                componentInstance: mockComponentInstance,
            }),
        };

        return TestBed.configureTestingModule({
            imports: [TestComponent, FaIconComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useValue: mockModalService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                debugElement = fixture.debugElement;
                dataExportConfirmationDialogService = TestBed.inject(DataExportConfirmationDialogService);
                translateService = TestBed.inject(TranslateService);
                translateSpy = jest.spyOn(translateService, 'instant');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be correctly initialized', () => {
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('artemisApp.dataExport.request');

        // Check that button was assigned with proper classes and type.
        const confirmButton = debugElement.query(By.css('.btn.btn-primary.btn-lg.me-1'));
        expect(confirmButton).not.toBeNull();
        expect(confirmButton.properties['type']).toBe('submit');

        // Check that button text span was added to the DOM.
        const buttonText = debugElement.query(By.css('.d-xl-inline'));
        expect(buttonText).not.toBeNull();
        expect(buttonText.nativeElement.textContent).toBe('artemisApp.dataExport.request');

        const directiveEl = debugElement.query(By.directive(DataExportRequestButtonDirective));
        expect(directiveEl).not.toBeNull();
        const directiveInstance = directiveEl.injector.get(DataExportRequestButtonDirective);
        expect(directiveInstance.expectedLogin()).toBe('login');
    });

    it('should on click call data export confirmation dialog service', fakeAsync(() => {
        // We ignore the console error because of ngForm not found, we only care about the dialog service call
        console.error = jest.fn();
        fixture.detectChanges();
        const dataExportConfirmationDialogSpy = jest.spyOn(dataExportConfirmationDialogService, 'openConfirmationDialog');
        const directiveEl = debugElement.query(By.directive(DataExportRequestButtonDirective));
        directiveEl.nativeElement.click();
        fixture.detectChanges();
        expect(dataExportConfirmationDialogSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));
});
