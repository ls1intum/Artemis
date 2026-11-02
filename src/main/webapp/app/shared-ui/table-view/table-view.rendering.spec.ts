import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TableViewComponent } from './table-view';

interface StudentRow {
    id: number;
    name: string;
}

describe('TableView rendered behavior', () => {
    let fixture: ComponentFixture<TableViewComponent<StudentRow>>;
    const students = [
        { id: 1, name: 'Ada Lovelace' },
        { id: 2, name: 'Grace Hopper' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TableViewComponent], providers: [{ provide: TranslateService, useClass: MockTranslateService }] }).compileComponents();
        fixture = TestBed.createComponent(TableViewComponent<StudentRow>);
        fixture.componentRef.setInput('cols', [{ field: 'name', header: 'Student', sort: true }]);
        fixture.componentRef.setInput('vals', students);
        fixture.componentRef.setInput('options', { lazy: false, selectionMode: 'multiple', showSearch: false });
        fixture.detectChanges();
    });

    it('renders native TUM UI table rows and protects disabled student selections', () => {
        fixture.componentRef.setInput('isRowDisabled', (row: StudentRow) => row.id === 2);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('table[tumAetUiTable]')).toBeTruthy();
        const checkboxes: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('tbody input[type="checkbox"]');
        expect(checkboxes).toHaveLength(2);
        expect(checkboxes[1].disabled).toBe(true);
        checkboxes[0].click();
        expect(fixture.componentInstance.tableSelection).toEqual([students[0]]);
    });

    it('sorts client rows without changing the input array', () => {
        const sortButton: HTMLButtonElement = fixture.nativeElement.querySelector('th button');
        sortButton.click();
        sortButton.click();
        fixture.detectChanges();
        const cells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('tbody tr td:last-child');
        const names = Array.from(cells).map((cell) => cell.textContent?.trim());
        expect(names).toEqual(['Grace Hopper', 'Ada Lovelace']);
        expect(students[0].name).toBe('Ada Lovelace');
    });
});
