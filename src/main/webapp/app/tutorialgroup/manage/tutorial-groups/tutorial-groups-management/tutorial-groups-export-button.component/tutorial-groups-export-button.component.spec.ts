import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TutorialGroupsExportButtonComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Directive, Input, signal } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpResourceRef } from '@angular/common/http';

@Directive({
    selector: '[jhiTranslate]',
})
class MockTranslateDirective {
    @Input() jhiTranslate: string;
}
describe('TutorialGroupsExportButtonComponent', () => {
    let component: TutorialGroupsExportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsExportButtonComponent>;
    const exampleCourseId = 1;

    let mockTutorialGroupsService: TutorialGroupsService;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        // Create the mock service with the necessary methods
        global.URL.createObjectURL = jest.fn();
        global.URL.revokeObjectURL = jest.fn();

        mockTutorialGroupsService = {
            exportTutorialGroupsToCSVResource: jest.fn(),
            exportTutorialGroupsToJSONResource: jest.fn(),
        } as any;

        mockAlertService = {
            error: jest.fn(),
        } as any;

        // Provide the mock service to the testing module
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsExportButtonComponent, MockComponent(FaIconComponent), MockTranslateDirective],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TutorialGroupsService, useValue: mockTutorialGroupsService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsExportButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', exampleCourseId);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the export dialog when the button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            result: Promise.resolve(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openExportDialog');

        const exportButton = fixture.debugElement.nativeElement.querySelector('#exportDialogButton');
        exportButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
        });
    }));

    it('should select all fields when toggleSelectAll is called', () => {
        component.toggleSelectAll();
        expect(component.selectAll).toBeTrue();
        expect(component.selectedFields).toHaveLength(component.availableFields.length);
        expect(component.availableFields.every((field) => field.selected)).toBeTrue();
    });

    it('should deselect all fields when toggleSelectAll is called twice', () => {
        component.toggleSelectAll();
        component.toggleSelectAll();
        expect(component.selectAll).toBeFalse();
        expect(component.selectedFields).toHaveLength(0);
        expect(component.availableFields.every((field) => !field.selected)).toBeTrue();
    });

    it('should update selected fields on field selection change', () => {
        const field = component.availableFields[0];
        component.onFieldSelectionChange(field);
        expect(field.selected).toBeTrue();
        expect(component.selectedFields).toContain(field.value);
    });

    it('should export CSV successfully', fakeAsync(() => {
        const blob = new Blob(['dummy data'], { type: 'text/csv' });
        const csvResource = {
            value: signal<Blob | undefined>(undefined),
            error: signal<unknown | undefined>(undefined),
            isLoading: signal(false),
        } as HttpResourceRef<Blob | undefined>;
        jest.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToCSVResource').mockReturnValue(csvResource);

        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = { close: jest.fn(), dismiss: jest.fn() };
        jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        component.exportCSV(mockModalRef as unknown as NgbModalRef);
        csvResource.value.set(blob);

        tick();
        expect(mockTutorialGroupsService.exportTutorialGroupsToCSVResource).toHaveBeenCalled();
    }));

    it('should handle CSV export error', fakeAsync(() => {
        const csvResource = {
            value: signal<Blob | undefined>(undefined),
            error: signal<unknown | undefined>(undefined),
            isLoading: signal(false),
        } as HttpResourceRef<Blob | undefined>;
        jest.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToCSVResource').mockReturnValue(csvResource);

        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = { close: jest.fn(), dismiss: jest.fn() };
        jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        component.exportCSV(mockModalRef as unknown as NgbModalRef);
        csvResource.error.set(new Error('CSV export failed'));

        tick();
        expect(mockTutorialGroupsService.exportTutorialGroupsToCSVResource).toHaveBeenCalled();
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedCSV');
        expect(mockModalRef.dismiss).toHaveBeenCalledWith('error');
    }));

    it('should export JSON successfully', fakeAsync(() => {
        const jsonResource = {
            value: signal<Array<any> | undefined>(undefined),
            error: signal<unknown | undefined>(undefined),
            isLoading: signal(false),
        } as HttpResourceRef<Array<any> | undefined>;
        jest.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToJSONResource').mockReturnValue(jsonResource);

        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = { close: jest.fn(), dismiss: jest.fn() };
        jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        component.exportJSON(mockModalRef as unknown as NgbModalRef);
        jsonResource.value.set([{ key: 'value' }]);

        tick();
        expect(mockTutorialGroupsService.exportTutorialGroupsToJSONResource).toHaveBeenCalled();
    }));

    it('should handle JSON export error', fakeAsync(() => {
        const jsonResource = {
            value: signal<Array<any> | undefined>(undefined),
            error: signal<unknown | undefined>(undefined),
            isLoading: signal(false),
        } as HttpResourceRef<Array<any> | undefined>;
        jest.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToJSONResource').mockReturnValue(jsonResource);

        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = { close: jest.fn(), dismiss: jest.fn() };
        jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        component.exportJSON(mockModalRef as unknown as NgbModalRef);
        jsonResource.error.set(new Error('JSON export failed'));

        tick();
        expect(mockTutorialGroupsService.exportTutorialGroupsToJSONResource).toHaveBeenCalled();
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedJSON');
        expect(mockModalRef.dismiss).toHaveBeenCalledWith('error');
    }));
});
