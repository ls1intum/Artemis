import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TutorialGroupsExportButtonComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Directive, input } from '@angular/core';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

@Directive({
    selector: '[jhiTranslate]',
})
class MockTranslateDirective {
    jhiTranslate = input<string>();
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
            exportTutorialGroupsToCSV: jest.fn().mockReturnValue(of(new Blob(['dummy data'], { type: 'text/csv' }))),
            exportToJson: jest.fn().mockReturnValue(of(new Blob(['{"key": "value"}'], { type: 'application/json' }))),
        } as any;

        mockAlertService = {
            error: jest.fn(),
        } as any;

        // Provide the mock service to the testing module
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsExportButtonComponent, MockComponent(FaIconComponent), MockTranslateDirective],
            providers: [
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
        const openDialogSpy = jest.spyOn(component, 'openExportDialog');

        const exportButton = fixture.debugElement.nativeElement.querySelector('#exportDialogButton');
        exportButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(component.dialogVisible()).toBeTrue();
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
        jest.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToCSV').mockReturnValue(of(blob));

        component.dialogVisible.set(true);
        component.exportCSV();

        tick();
        expect(mockTutorialGroupsService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(component.dialogVisible()).toBeFalse();
    }));

    it('should handle CSV export error', fakeAsync(() => {
        jest.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToCSV').mockReturnValue(throwError(() => new Error('CSV export failed')));

        component.dialogVisible.set(true);
        component.exportCSV();

        tick();
        expect(mockTutorialGroupsService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedCSV');
        expect(component.dialogVisible()).toBeFalse();
    }));

    it('should export JSON successfully', fakeAsync(() => {
        const response = new Blob(['{"key": "value"}'], { type: 'application/json' }).type;
        jest.spyOn(mockTutorialGroupsService, 'exportToJson').mockReturnValue(of(response));

        component.dialogVisible.set(true);
        component.exportJSON();

        tick();
        expect(mockTutorialGroupsService.exportToJson).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(component.dialogVisible()).toBeFalse();
    }));

    it('should handle JSON export error', fakeAsync(() => {
        jest.spyOn(mockTutorialGroupsService, 'exportToJson').mockReturnValue(throwError(() => new Error('JSON export failed')));

        component.dialogVisible.set(true);
        component.exportJSON();

        tick();
        expect(mockTutorialGroupsService.exportToJson).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedJSON');
        expect(component.dialogVisible()).toBeFalse();
    }));
});
