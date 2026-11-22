import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { EventEmitter } from '@angular/core';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DeleteDialogComponent', () => {
    let comp: DeleteDialogComponent;
    let fixture: ComponentFixture<DeleteDialogComponent>;
    let dialogRef: TumAetUiDialogRef;
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
            imports: [ReactiveFormsModule, FormsModule, DeleteDialogComponent],
            providers: [
                JhiLanguageHelper,
                AlertService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TumAetUiDialogRef, useValue: mockDialogRef },
                { provide: TumAetUiDialogConfig, useValue: createMockDialogConfig() },
                provideTranslateService(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(DeleteDialogComponent);
        comp = fixture.componentInstance;
        dialogRef = TestBed.inject(TumAetUiDialogRef);
    });

    it('Dialog is correctly initialized', () => {
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        expect(comp.entityTitle()).toBe('title');
        expect(comp.deleteQuestion()).toBe('artemisApp.exercise.delete.question');
        expect(comp.warningTextColor).toBe('text-state-danger');
        expect(comp.useFaCheckIcon()).toBe(false);

        // Check that clear method calls dialogRef.close
        comp.clear();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should show an icon-sized spinner while the summary loads', () => {
        fixture.detectChanges();
        comp.isLoadingSummary.set(true);
        fixture.detectChanges();

        // The kit spinner is 100px wide by default. A utility class cannot shrink it, as the kit sets its size in unlayered styles.
        const spinner: HTMLElement | null = fixture.nativeElement.querySelector('.summary-loading-container tumaet-ui-progress-spinner');
        expect(spinner).not.toBeNull();
        expect(spinner!.style.width).toBe('1.5rem');
        expect(spinner!.style.height).toBe('1.5rem');
    });

    it('Form properly checked before submission', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        // Initially the form should be invalid (empty value doesn't match 'title')
        expect(comp.deleteForm.invalid).toBe(true);

        // User entered some title (wrong title)
        comp.confirmEntityName = 'some title';
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.deleteForm.invalid).toBe(true);

        // User entered correct title
        comp.confirmEntityName = 'title';
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.deleteForm.invalid).toBe(false);
    });

    it('Dialog closes immediately when confirmDelete is called', () => {
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        // external component delete method was executed
        comp.confirmDelete();

        // submit should be disabled and dialog should close immediately
        expect(comp.submitDisabled()).toBe(true);
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

    it('exposes each flat summary label as the header of its value', () => {
        fixture.detectChanges();
        comp.entitySummary.set({ exercises: 2, participations: 0, hidden: undefined });
        fixture.detectChanges();

        const rows = Array.from(fixture.nativeElement.querySelectorAll('table tbody tr')) as HTMLTableRowElement[];
        expect(rows).toHaveLength(2);
        expect(rows.map((row) => row.querySelector('th[scope="row"] span')?.textContent?.trim())).toEqual(['exercises', 'participations']);
        expect(rows.map((row) => row.querySelector('td')?.textContent?.trim())).toEqual(['2', '0']);
    });

    it('associates categorized values with only their own label and category across paired and odd rows', () => {
        fixture.detectChanges();
        comp.categorizedEntitySummary.set([
            {
                titleKey: 'course',
                items: [
                    { labelKey: 'exercises', value: 2 },
                    { labelKey: 'participations', value: 0 },
                    { labelKey: 'students', value: 7 },
                ],
            },
            {
                titleKey: 'exam',
                items: [
                    { labelKey: 'exercises', value: 5 },
                    { labelKey: 'hidden', value: undefined },
                ],
            },
        ]);
        fixture.detectChanges();

        const table = fixture.nativeElement.querySelector('table') as HTMLTableElement;
        const values = Array.from(table.querySelectorAll('td')).filter((cell) => cell.textContent?.trim());
        expect(
            values.map((cell) => {
                const headers = cell.headers
                    .split(' ')
                    .filter(Boolean)
                    .map((id) => table.querySelector(`th[id="${id}"] span`)?.textContent?.trim());
                return { value: cell.textContent?.trim(), headers };
            }),
        ).toEqual([
            { value: '2', headers: ['course', 'exercises'] },
            { value: '0', headers: ['course', 'participations'] },
            { value: '7', headers: ['course', 'students'] },
            { value: '5', headers: ['exam', 'exercises'] },
        ]);
    });
});
