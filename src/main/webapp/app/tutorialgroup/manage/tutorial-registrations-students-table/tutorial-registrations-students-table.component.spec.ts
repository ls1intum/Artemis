import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TutorialRegistrationsStudentsTableComponent } from './tutorial-registrations-students-table.component';
import { TutorialRegistrationsStudentsTableRemoveActionColumnInfo } from './tutorial-registrations-students-table.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorial-group-student';

describe('TutorialRegistrationsStudentsTable', () => {
    let fixture: ComponentFixture<TutorialRegistrationsStudentsTableComponent>;

    const firstStudent: TutorialGroupStudent = {
        id: 1,
        name: 'Ada Lovelace',
        login: 'ada',
        email: 'ada@tum.de',
        registrationNumber: 'ge58vol',
        profilePictureUrl: undefined,
    };

    const secondStudent: TutorialGroupStudent = {
        id: 2,
        name: 'Alan Turing',
        login: 'alan',
        email: 'alan@tum.de',
        registrationNumber: 'gv52net',
        profilePictureUrl: undefined,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsStudentsTableComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsStudentsTableComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should associate column headers and data rows in the same table', () => {
        fixture.componentRef.setInput('students', [firstStudent]);
        fixture.detectChanges();

        const tables = fixture.nativeElement.querySelectorAll('table');
        expect(tables).toHaveLength(1);
        expect(tables[0].querySelectorAll('thead th[scope="col"]')).toHaveLength(4);
        expect(tables[0].querySelectorAll('tbody tr')).toHaveLength(1);
    });

    it('should render one table row per input student', () => {
        fixture.componentRef.setInput('students', [firstStudent, secondStudent]);
        fixture.detectChanges();

        const rows = fixture.nativeElement.querySelectorAll('tbody tr');

        expect(rows).toHaveLength(2);
        expect(rows[0].textContent).toContain('Ada Lovelace');
        expect(rows[0].textContent).toContain('ada');
        expect(rows[0].textContent).toContain('ada@tum.de');
        expect(rows[0].textContent).toContain('ge58vol');
        expect(rows[1].textContent).toContain('Alan Turing');
        expect(rows[1].textContent).toContain('alan');
        expect(rows[1].textContent).toContain('alan@tum.de');
        expect(rows[1].textContent).toContain('gv52net');
    });

    it('should not display the remove column when no column info is provided', () => {
        fixture.componentRef.setInput('students', [firstStudent]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('th.action-column')).toBeNull();
        expect(fixture.nativeElement.querySelector('td.action-column')).toBeNull();
        expect(fixture.nativeElement.querySelector('button[aria-label="entity.action.remove"]')).toBeNull();
    });

    it('should display the remove column when column info is provided', () => {
        const removeActionColumnInfo: TutorialRegistrationsStudentsTableRemoveActionColumnInfo = {
            headerStringKey: 'artemisApp.pages.tutorialGroupRegistrations.studentsTableHeaderLabel.deregister',
            onRemove: vi.fn(),
        };

        fixture.componentRef.setInput('students', [firstStudent]);
        fixture.componentRef.setInput('removeActionColumnInfo', removeActionColumnInfo);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('th.action-column')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('td.action-column')).not.toBeNull();
        const remove = fixture.nativeElement.querySelector('button[aria-label="entity.action.remove"]') as HTMLButtonElement;
        expect(remove).not.toBeNull();
        remove.click();
        expect(removeActionColumnInfo.onRemove).toHaveBeenCalledWith(expect.any(MouseEvent), firstStudent);
    });
    describe('sorting', () => {
        const thirdStudent: TutorialGroupStudent = { id: 3, name: 'grace Hopper', login: 'grace', email: 'grace@tum.de', registrationNumber: 'ab12cde' };
        const names = () =>
            Array.from(fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>).map((row) => row.querySelector('td span')!.lastChild!.textContent!.trim());

        beforeEach(() => {
            fixture.componentRef.setInput('students', [thirdStudent, secondStudent, firstStudent]);
            fixture.detectChanges();
        });

        it('should sort by name ascending by default, ignoring case', () => {
            expect(fixture.componentInstance.sortField()).toBe('name');
            expect(fixture.componentInstance.sortOrder()).toBe(1);
            expect(names()).toEqual(['Ada Lovelace', 'Alan Turing', 'grace Hopper']);
        });

        it('should store the sort event and reorder the rows', () => {
            fixture.componentInstance.onSort({ field: 'login', order: -1 });
            fixture.detectChanges();

            expect(fixture.componentInstance.sortField()).toBe('login');
            expect(fixture.componentInstance.sortOrder()).toBe(-1);
            expect(names()).toEqual(['grace Hopper', 'Alan Turing', 'Ada Lovelace']);
        });

        it('should sort by registration number and treat missing values as empty', () => {
            fixture.componentRef.setInput('students', [thirdStudent, { ...secondStudent, registrationNumber: undefined }, firstStudent]);
            fixture.componentInstance.onSort({ field: 'registrationNumber', order: 1 });
            fixture.detectChanges();

            expect(names()).toEqual(['Alan Turing', 'grace Hopper', 'Ada Lovelace']);
        });

        const sortButton = (columnIndex: number) =>
            fixture.nativeElement.querySelectorAll('thead th[scope="col"]')[columnIndex].querySelector('button') as HTMLButtonElement | null;

        it('should render a sort button in every sortable header, so the translation does not replace it', () => {
            // A jhiTranslate directly on the header writes its host's content and would wipe the button.
            for (const columnIndex of [0, 1, 2, 3]) {
                expect(sortButton(columnIndex)).not.toBeNull();
            }
        });

        it('should reorder the rows when a sortable header is clicked', () => {
            sortButton(2)!.click();
            fixture.detectChanges();

            expect(fixture.componentInstance.sortField()).toBe('email');
            expect(names()).toEqual(['Ada Lovelace', 'Alan Turing', 'grace Hopper']);
        });

        it('should sort by registration number when its header button is clicked', () => {
            sortButton(3)!.click();
            fixture.detectChanges();

            expect(fixture.componentInstance.sortField()).toBe('registrationNumber');
            expect(fixture.componentInstance.sortOrder()).toBe(1);
            expect(names()).toEqual(['grace Hopper', 'Ada Lovelace', 'Alan Turing']);

            sortButton(3)!.click();
            fixture.detectChanges();

            expect(fixture.componentInstance.sortOrder()).toBe(-1);
            expect(names()).toEqual(['Alan Turing', 'Ada Lovelace', 'grace Hopper']);
        });
    });

    it('should show the toolbar slot only when hasToolbar is set', () => {
        fixture.componentRef.setInput('students', [firstStudent]);
        fixture.detectChanges();
        const toolbar = () => fixture.nativeElement.querySelector('[tumAetUiTableToolbar]') as HTMLElement;
        expect(toolbar().hidden).toBe(true);

        fixture.componentRef.setInput('hasToolbar', true);
        fixture.detectChanges();
        expect(toolbar().hidden).toBe(false);
    });
});
