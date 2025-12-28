import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TutorialGroupsExportButtonComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorialGroupsExportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupsExportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsExportButtonComponent>;
    const exampleCourseId = 1;

    let mockTutorialGroupsService: TutorialGroupsService;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        // Create the mock service with the necessary methods
        global.URL.createObjectURL = vi.fn();
        global.URL.revokeObjectURL = vi.fn();

        mockTutorialGroupsService = {
            exportTutorialGroupsToCSV: vi.fn().mockReturnValue(of(new Blob(['dummy data'], { type: 'text/csv' }))),
            exportToJson: vi.fn().mockReturnValue(of(new Blob(['{"key": "value"}'], { type: 'application/json' }))),
        } as any;

        mockAlertService = {
            error: vi.fn(),
        } as any;

        // Provide the mock service to the testing module
        await TestBed.configureTestingModule({
            imports: [TutorialGroupsExportButtonComponent],
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
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the export dialog when the button is clicked', () => {
        // Test the method directly to avoid jsdom CSS parsing issues with PrimeNG dialog
        const mockEvent = { stopPropagation: vi.fn() } as unknown as MouseEvent;
        expect(component.dialogVisible()).toBe(false);
        component.openExportDialog(mockEvent);
        expect(component.dialogVisible()).toBe(true);
        expect(mockEvent.stopPropagation).toHaveBeenCalled();
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
        vi.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToCSV').mockReturnValue(of(blob));

        component.dialogVisible.set(true);
        component.exportCSV();

        expect(mockTutorialGroupsService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(component.dialogVisible()).toBe(false);
    });

    it('should handle CSV export error', () => {
        vi.spyOn(mockTutorialGroupsService, 'exportTutorialGroupsToCSV').mockReturnValue(throwError(() => new Error('CSV export failed')));

        component.dialogVisible.set(true);
        component.exportCSV();

        expect(mockTutorialGroupsService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedCSV');
        expect(component.dialogVisible()).toBe(false);
    });

    it('should export JSON successfully', () => {
        const response = new Blob(['{"key": "value"}'], { type: 'application/json' }).type;
        vi.spyOn(mockTutorialGroupsService, 'exportToJson').mockReturnValue(of(response));

        component.dialogVisible.set(true);
        component.exportJSON();

        expect(mockTutorialGroupsService.exportToJson).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(component.dialogVisible()).toBe(false);
    });

    it('should handle JSON export error', () => {
        vi.spyOn(mockTutorialGroupsService, 'exportToJson').mockReturnValue(throwError(() => new Error('JSON export failed')));

        component.dialogVisible.set(true);
        component.exportJSON();

        expect(mockTutorialGroupsService.exportToJson).toHaveBeenCalledWith(exampleCourseId, component.selectedFields);
        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.tutorialGroupExportDialog.failedJSON');
        expect(component.dialogVisible()).toBe(false);
    });
});
