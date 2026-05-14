import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TutorialGroupsExportButtonComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { AlertService } from 'app/shared/service/alert.service';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupExportData } from 'app/openapi/model/tutorialGroupExportData';

interface TutorialGroupApiServiceMock {
    exportTutorialGroupsToCSV: ReturnType<typeof vi.fn>;
    exportTutorialGroupsToJSON: ReturnType<typeof vi.fn>;
}

interface AlertServiceMock {
    error: ReturnType<typeof vi.fn>;
}

describe('TutorialGroupsExportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupsExportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsExportButtonComponent>;
    const exampleCourseId = 1;

    let mockTutorialGroupApiService: TutorialGroupApiServiceMock;
    let mockAlertService: AlertServiceMock;

    beforeEach(async () => {
        // Create the mock service with the necessary methods
        global.URL.createObjectURL = vi.fn();
        global.URL.revokeObjectURL = vi.fn();

        mockTutorialGroupApiService = {
            exportTutorialGroupsToCSV: vi.fn().mockReturnValue(of(new Blob(['dummy data'], { type: 'text/csv' }))),
            exportTutorialGroupsToJSON: vi.fn().mockReturnValue(of([{ title: 'Tutorial Group 1' }] satisfies TutorialGroupExportData[])),
        };

        mockAlertService = {
            error: vi.fn(),
        };

        // Provide the mock service to the testing module
        await TestBed.configureTestingModule({
            imports: [TutorialGroupsExportButtonComponent],
            providers: [
                { provide: TutorialGroupApiService, useValue: mockTutorialGroupApiService },
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
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the export dialog when the button is clicked', () => {
        // Test the method directly to avoid jsdom CSS parsing issues with PrimeNG dialog
        const mockEvent = new MouseEvent('click');
        const stopPropagationSpy = vi.spyOn(mockEvent, 'stopPropagation');
        expect(component.dialogVisible()).toBe(false);
        component.openExportDialog(mockEvent);
        expect(component.dialogVisible()).toBe(true);
        expect(stopPropagationSpy).toHaveBeenCalled();
    });

    it('should select all fields when toggleSelectAll is called', () => {
        component.toggleSelectAll();
        expect(component.selectAll).toBe(true);
        expect(component.selectedFields).toHaveLength(component.availableFields.length);
        expect(component.availableFields.every((field) => field.selected)).toBe(true);
    });

    it('should deselect all fields when toggleSelectAll is called twice', () => {
        component.toggleSelectAll();
        component.toggleSelectAll();
        expect(component.selectAll).toBe(false);
        expect(component.selectedFields).toHaveLength(0);
        expect(component.availableFields.every((field) => !field.selected)).toBe(true);
    });

    it('should update selected fields on field selection change', () => {
        const field = component.availableFields[0];
        component.onFieldSelectionChange(field);
        expect(field.selected).toBe(true);
        expect(component.selectedFields).toContain(field.value);
    });

    it('should export CSV successfully', () => {
        const blob = new Blob(['dummy data'], { type: 'text/csv' });
        mockTutorialGroupApiService.exportTutorialGroupsToCSV.mockReturnValue(of(blob));

        component.dialogVisible.set(true);
        component.exportCSV();

        expect(mockTutorialGroupApiService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(component.dialogVisible()).toBe(false);
    });

    it('should handle CSV export error', () => {
        mockTutorialGroupApiService.exportTutorialGroupsToCSV.mockReturnValue(throwError(() => new Error('CSV export failed')));

        component.dialogVisible.set(true);
        component.exportCSV();

        expect(mockTutorialGroupApiService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedCSV');
        expect(component.dialogVisible()).toBe(false);
    });

    it('should export JSON successfully', () => {
        mockTutorialGroupApiService.exportTutorialGroupsToJSON.mockReturnValue(of([{ title: 'Tutorial Group 1' }] satisfies TutorialGroupExportData[]));

        component.dialogVisible.set(true);
        component.exportJSON();

        expect(mockTutorialGroupApiService.exportTutorialGroupsToJSON).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(component.dialogVisible()).toBe(false);
    });

    it('should handle JSON export error', () => {
        mockTutorialGroupApiService.exportTutorialGroupsToJSON.mockReturnValue(throwError(() => new Error('JSON export failed')));

        component.dialogVisible.set(true);
        component.exportJSON();

        expect(mockTutorialGroupApiService.exportTutorialGroupsToJSON).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedJSON');
        expect(component.dialogVisible()).toBe(false);
    });
});
