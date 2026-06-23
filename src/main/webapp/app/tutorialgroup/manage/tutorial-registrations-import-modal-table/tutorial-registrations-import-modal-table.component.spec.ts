import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TutorialRegistrationsImportModalTableComponent, TutorialRegistrationsImportModalTableRow } from './tutorial-registrations-import-modal-table.component';

describe('TutorialRegistrationsImportModalTableComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsImportModalTableComponent;
    let fixture: ComponentFixture<TutorialRegistrationsImportModalTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsImportModalTableComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsImportModalTableComponent);
        component = fixture.componentInstance;
    });

    it('should render one body row per input row with login and registration number values', () => {
        const rows: TutorialRegistrationsImportModalTableRow[] = [
            { login: 'ada', registrationNumber: 'R001', markFilledCells: false },
            { login: 'alan', registrationNumber: 'R002', markFilledCells: false },
        ];

        fixture.componentRef.setInput('rows', rows);
        fixture.detectChanges();

        const renderedRows = fixture.nativeElement.querySelectorAll('tbody tr');

        expect(renderedRows).toHaveLength(2);
        expect(renderedRows[0].querySelectorAll('td')[0].textContent.trim()).toBe('ada');
        expect(renderedRows[0].querySelectorAll('td')[1].textContent.trim()).toBe('R001');
        expect(renderedRows[1].querySelectorAll('td')[0].textContent.trim()).toBe('alan');
        expect(renderedRows[1].querySelectorAll('td')[1].textContent.trim()).toBe('R002');
        expect(component).toBeTruthy();
    });

    it('should not mark filled cells as danger when markFilledCells is false', () => {
        fixture.componentRef.setInput('rows', [{ login: 'ada', registrationNumber: 'R001', markFilledCells: false }]);
        fixture.detectChanges();

        const cells = fixture.nativeElement.querySelectorAll('tbody tr td');

        expect(cells[0].classList.contains('danger')).toBe(false);
        expect(cells[1].classList.contains('danger')).toBe(false);
    });

    it('should mark filled cells as danger when markFilledCells is true', () => {
        fixture.componentRef.setInput('rows', [{ login: 'ada', registrationNumber: 'R001', markFilledCells: true }]);
        fixture.detectChanges();

        const cells = fixture.nativeElement.querySelectorAll('tbody tr td');

        expect(cells[0].classList.contains('danger')).toBe(true);
        expect(cells[1].classList.contains('danger')).toBe(true);
    });

    it('should only mark populated cells as danger and render missing values as empty', () => {
        const rows: TutorialRegistrationsImportModalTableRow[] = [
            { login: 'ada', registrationNumber: undefined, markFilledCells: true },
            { login: undefined, registrationNumber: 'R002', markFilledCells: true },
        ];

        fixture.componentRef.setInput('rows', rows);
        fixture.detectChanges();

        const renderedRows = fixture.nativeElement.querySelectorAll('tbody tr');
        const firstRowCells = renderedRows[0].querySelectorAll('td');
        const secondRowCells = renderedRows[1].querySelectorAll('td');

        expect(firstRowCells[0].textContent.trim()).toBe('ada');
        expect(firstRowCells[1].textContent.trim()).toBe('');
        expect(firstRowCells[0].classList.contains('danger')).toBe(true);
        expect(firstRowCells[1].classList.contains('danger')).toBe(false);

        expect(secondRowCells[0].textContent.trim()).toBe('');
        expect(secondRowCells[1].textContent.trim()).toBe('R002');
        expect(secondRowCells[0].classList.contains('danger')).toBe(false);
        expect(secondRowCells[1].classList.contains('danger')).toBe(true);
    });
});
