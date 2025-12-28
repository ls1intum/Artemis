import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TutorialGroupsImportButtonComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-import-button/tutorial-groups-import-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorialGroupsImportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupsImportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsImportButtonComponent>;
    const exampleCourseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupsImportButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsImportButtonComponent);
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

    it('should open the import dialog when the button is clicked', () => {
        const importDialog = component.importDialog();
        expect(importDialog).toBeDefined();
        const openSpy = vi.spyOn(importDialog!, 'open');
        const openDialogSpy = vi.spyOn(component, 'openTutorialGroupImportDialog');

        const importButton = fixture.debugElement.nativeElement.querySelector('#importDialogButton');
        importButton.click();

        fixture.detectChanges();
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledOnce();
    });

    it('should show warning dialog when import is completed', () => {
        // Test the component method directly without rendering the dialog
        expect(component.warningDialogVisible()).toBe(false);
        component.onImportCompleted();
        expect(component.warningDialogVisible()).toBe(true);
    });

    it('should close warning dialog and emit importFinished when closeWarningDialog is called', () => {
        const importFinishedSpy = vi.spyOn(component.importFinished, 'emit');
        // Set the signal directly without triggering dialog render
        (component as any).warningDialogVisible.set(true);

        component.closeWarningDialog();

        expect(component.warningDialogVisible()).toBe(false);
        expect(importFinishedSpy).toHaveBeenCalledOnce();
    });
});
