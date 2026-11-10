import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { User } from 'app/account/user/user.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { PresentationAssessmentInstancesTableComponent } from 'app/presentation/manage/presentation-assessment-instances-table.component';
import { PresentationStudentTableRow } from 'app/presentation/manage/presentation-assessment-management.helper';
import { PresentationAssessment, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationAssessmentInstancesTableComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentInstancesTableComponent>;
    let component: PresentationAssessmentInstancesTableComponent;

    const presentationAssessment: PresentationAssessment = { id: 1, title: 'Final', maxPoints: 20 };

    function createRow(overrides: Partial<PresentationStudentTableRow> = {}): PresentationStudentTableRow {
        return {
            studentLogin: 'student1',
            student: hydrate(new User(), { login: 'student1', name: 'Student One', email: 'student1@example.org' }),
            presentationAssessment,
            instance: { id: 10, resultPoints: 12, mode: PresentationAssessmentMode.IN_PERSON, location: 'Room 1' },
            rowKey: '10:student1',
            displayName: 'Student One',
            assessed: true,
            ...overrides,
        };
    }

    const element = () => fixture.nativeElement as HTMLElement;
    const query = (selector: string) => element().querySelector<HTMLElement>(selector);

    function render(rows: PresentationStudentTableRow[], canDelete = false) {
        fixture.componentRef.setInput('rows', rows);
        fixture.componentRef.setInput('presentationAssessment', presentationAssessment);
        fixture.componentRef.setInput('canDelete', canDelete);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentInstancesTableComponent],
            providers: [provideArtemisTumAetUiTranslator(), { provide: TranslateService, useClass: MockTranslateService }, { provide: DialogService, useClass: MockDialogService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentInstancesTableComponent);
        component = fixture.componentInstance;
    });

    it('should render one row per student and no rows when there are none', () => {
        render([createRow(), createRow({ studentLogin: 'student2', rowKey: '11:student2', displayName: 'Student Two' })]);
        expect(element().querySelectorAll('tbody tr')).toHaveLength(2);

        render([]);
        expect(element().querySelectorAll('tbody tr')).toHaveLength(0);
    });

    it('should show the result points against the maximum only for assessed rows', () => {
        render([createRow()]);
        expect(query('[data-testid="instance-result-points"]')?.textContent).toContain('12 / 20');

        render([createRow({ assessed: false, instance: { id: 10 } })]);
        expect(query('[data-testid="instance-result-points"]')).toBeNull();
    });

    it('should offer the delete button only when deleting is allowed', () => {
        render([createRow()]);
        expect(query('[data-testid="delete-instance-button"]')).toBeNull();

        render([createRow()], true);
        expect(query('[data-testid="delete-instance-button"]')).not.toBeNull();
    });

    it('should emit the instance when edit is clicked', () => {
        const row = createRow();
        const editSpy = vi.fn();
        component.editInstance.subscribe(editSpy);
        render([row]);

        query('[data-testid="edit-instance-button"] button')!.click();

        expect(editSpy).toHaveBeenCalledWith(row);
    });

    it('should show and hide the details of a row when the student is clicked', () => {
        render([createRow()]);
        const toggle = query('[data-testid="toggle-row-details"]')!;
        expect(query('.presentation-detail-row')).toBeNull();

        toggle.click();
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        expect(query('#presentation-detail-10\\:student1')?.textContent).toContain('Room 1');

        toggle.click();
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect(query('.presentation-detail-row')).toBeNull();
    });

    it('should keep rows expanded while the rows reload and collapse them for another presentation', () => {
        render([createRow()]);
        query('[data-testid="toggle-row-details"]')!.click();
        fixture.detectChanges();

        render([createRow()]);
        expect(query('.presentation-detail-row')).not.toBeNull();

        fixture.componentRef.setInput('presentationAssessment', { id: 2, title: 'Midterm', maxPoints: 20 });
        fixture.detectChanges();
        expect(query('.presentation-detail-row')).toBeNull();
    });
    describe('sorting', () => {
        const rows = () => [
            createRow({ studentLogin: 'b', rowKey: '1:b', displayName: 'bob', assessed: true, instance: { id: 1, resultPoints: 5, presentationDate: dayjs('2026-02-01') } }),
            createRow({ studentLogin: 'a', rowKey: '2:a', displayName: 'Alice', assessed: false, instance: { id: 2 } }),
            createRow({ studentLogin: 'c', rowKey: '3:c', displayName: 'carol', assessed: true, instance: { id: 3, resultPoints: 15, presentationDate: dayjs('2026-01-01') } }),
        ];
        const order = () => Array.from(element().querySelectorAll<HTMLElement>('[data-testid="toggle-row-details"]')).map((el) => el.textContent!.trim());
        const sortBy = (field: string, direction: 1 | -1) => {
            component['onSortChange']({ field, order: direction });
            fixture.detectChanges();
        };

        it('should keep the incoming order until a column is selected', () => {
            render(rows());
            expect(component['sortField']()).toBeUndefined();
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['b', 'a', 'c']);
        });

        it('should store the sort event and sort by the lower-cased display name', () => {
            render(rows());
            sortBy('displayName', 1);
            expect(component['sortField']()).toBe('displayName');
            expect(component['sortOrder']()).toBe(1);
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['a', 'b', 'c']);
            sortBy('displayName', -1);
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['c', 'b', 'a']);
        });

        it('should sort pending rows before assessed ones ascending by status', () => {
            render(rows());
            sortBy('assessed', 1);
            expect(component['sortedRows']()[0].studentLogin).toBe('a');
            sortBy('assessed', -1);
            expect(component['sortedRows']()[2].studentLogin).toBe('a');
        });

        it('should sort by result points and treat missing points as the lowest value', () => {
            render(rows());
            sortBy('resultPoints', 1);
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['a', 'b', 'c']);
            sortBy('resultPoints', -1);
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['c', 'b', 'a']);
        });

        it('should sort by presentation date and treat missing dates as the earliest', () => {
            render(rows());
            sortBy('presentationDate', 1);
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['a', 'c', 'b']);
            sortBy('presentationDate', -1);
            expect(component['sortedRows']().map((row) => row.studentLogin)).toEqual(['b', 'c', 'a']);
        });

        it('should render the rows in the selected order when a sortable header is clicked', () => {
            render(rows());
            const header = query('th[tumAetUiSortableColumn="displayName"]')!;
            const trigger = (header.querySelector('button') ?? header) as HTMLElement;
            trigger.click();
            fixture.detectChanges();

            expect(component['sortField']()).toBe('displayName');
            expect(order()).toHaveLength(3);
            expect(order()[0]).toContain('Alice');
        });
    });
});
