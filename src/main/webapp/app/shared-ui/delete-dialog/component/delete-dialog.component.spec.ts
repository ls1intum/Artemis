import { vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { EventEmitter } from '@angular/core';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DeleteDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: DeleteDialogComponent;
    let fixture: ComponentFixture<DeleteDialogComponent>;
    let dialogRef: DynamicDialogRef;
    let dialogErrorSource: Subject<string>;

    const createMockDialogConfig = (overrides = {}) => {
        dialogErrorSource = new Subject<string>();
        return {
            data: {
                entityTitle: 'title',
                deleteQuestion: 'artemisApp.exercise.delete.question',
                translateValues: { title: 'title' },
                deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
                additionalChecks: undefined,
                entitySummaryTitle: undefined,
                actionType: ActionType.Delete,
                buttonType: ButtonType.ERROR,
                delete: new EventEmitter<{ [key: string]: boolean }>(),
                dialogError: dialogErrorSource.asObservable(),
                requireConfirmationOnlyForAdditionalChecks: false,
                fetchEntitySummary: undefined,
                fetchCategorizedEntitySummary: undefined,
                ...overrides,
            },
        };
    };

    beforeEach(async () => {
        const mockDialogRef = {
            close: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ReactiveFormsModule, FormsModule, DeleteDialogComponent],
            providers: [
                JhiLanguageHelper,
                AlertService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: mockDialogRef },
                { provide: DynamicDialogConfig, useValue: createMockDialogConfig() },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(DeleteDialogComponent);
        comp = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
    });

    it('Dialog is correctly initialized', () => {
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        expect(comp.entityTitle()).toBe('title');
        expect(comp.deleteQuestion).toBe('artemisApp.exercise.delete.question');
        expect(comp.warningTextColor).toBe('text-danger');
        expect(comp.useFaCheckIcon).toBeFalse();

        // Check that clear method calls dialogRef.close
        comp.clear();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('Form properly checked before submission', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        // Initially the form should be invalid (empty value doesn't match 'title')
        expect(comp.deleteForm.invalid).toBeTrue();

        // User entered some title (wrong title)
        comp.confirmEntityName = 'some title';
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.deleteForm.invalid).toBeTrue();

        // User entered correct title
        comp.confirmEntityName = 'title';
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.deleteForm.invalid).toBeFalse();
    });

    it('Dialog closes immediately when confirmDelete is called', () => {
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        // external component delete method was executed
        comp.confirmDelete();

        // submit should be disabled and dialog should close immediately
        expect(comp.submitDisabled()).toBeTrue();
        expect(closeSpy).toHaveBeenCalledOnce();

        // Note: Error handling is now done in DeleteDialogService, not in the component.
        // The dialog closes immediately so the progress bar can be shown during deletion.

        fixture.destroy();
    });

    it('getItemPairs should correctly group items', () => {
        fixture.detectChanges();
        const items = [
            { labelKey: 'label1', value: 1 },
            { labelKey: 'label2', value: 2 },
            { labelKey: 'label3', value: 3 },
            { labelKey: 'label4', value: undefined },
        ];
        const pairs = comp.getItemPairs(items);
        expect(pairs).toHaveLength(2);
        expect(pairs[0]).toHaveLength(2);
        expect(pairs[1]).toHaveLength(1);
    });
});
