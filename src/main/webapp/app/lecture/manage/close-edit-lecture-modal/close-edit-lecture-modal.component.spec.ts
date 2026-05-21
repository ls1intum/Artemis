import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CloseEditLectureModalComponent } from 'app/lecture/manage/close-edit-lecture-modal/close-edit-lecture-modal.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

describe('CloseEditLectureModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CloseEditLectureModalComponent;
    let fixture: ComponentFixture<CloseEditLectureModalComponent>;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [CloseEditLectureModalComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                {
                    provide: DynamicDialogConfig,
                    useValue: {
                        data: {
                            hasUnsavedChangesInTitleSection: true,
                            hasUnsavedChangesInPeriodSection: false,
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CloseEditLectureModalComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize signals from dialog config data', () => {
        expect(component.hasUnsavedChangesInTitleSection()).toBe(true);
        expect(component.hasUnsavedChangesInPeriodSection()).toBe(false);
    });

    it('should close dialog with true when confirming', () => {
        component.closeWindow(true);

        expect(dialogRefCloseSpy).toHaveBeenCalledWith(true);
    });

    it('should close dialog with false when canceling', () => {
        component.closeWindow(false);

        expect(dialogRefCloseSpy).toHaveBeenCalledWith(false);
    });
});
