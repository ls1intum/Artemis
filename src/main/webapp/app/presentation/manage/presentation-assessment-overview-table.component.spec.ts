import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { User } from 'app/account/user/user.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { PresentationAssessmentOverviewTableComponent } from 'app/presentation/manage/presentation-assessment-overview-table.component';
import { PresentationStudentTableRow } from 'app/presentation/manage/presentation-assessment-management.helper';
import { PresentationAssessment, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationAssessmentOverviewTableComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentOverviewTableComponent>;
    let component: PresentationAssessmentOverviewTableComponent;

    const standalonePresentation: PresentationAssessment = { id: 1, title: 'Final', maxPoints: 20, description: 'Ten minutes' };
    const exercisePresentation: PresentationAssessment = { id: 2, title: 'Sorting talk', maxPoints: 10, exerciseId: 5, exerciseTitle: 'Sorting' };

    function createRow(overrides: Partial<PresentationStudentTableRow> = {}): PresentationStudentTableRow {
        return {
            studentLogin: 'student1',
            student: hydrate(new User(), { login: 'student1', name: 'Student One', email: 'student1@example.org' }),
            presentationAssessment: standalonePresentation,
            instance: { id: 10, resultPoints: 12, mode: PresentationAssessmentMode.IN_PERSON, location: 'Room 1' },
            rowKey: '10:student1',
            displayName: 'Student One',
            assessed: true,
            ...overrides,
        };
    }

    const exerciseRow = () => createRow({ presentationAssessment: exercisePresentation, instance: { id: 11, resultPoints: 7 }, studentLogin: 'student2', rowKey: '11:student2' });

    const element = () => fixture.nativeElement as HTMLElement;
    const query = (selector: string) => element().querySelector<HTMLElement>(selector);
    const bodyRows = () => Array.from(element().querySelectorAll<HTMLElement>('tbody tr'));

    function render(rows: PresentationStudentTableRow[], canDelete = false) {
        fixture.componentRef.setInput('rows', rows);
        fixture.componentRef.setInput('sortField', 'studentLogin');
        fixture.componentRef.setInput('sortOrder', 1);
        fixture.componentRef.setInput('canDelete', canDelete);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentOverviewTableComponent],
            providers: [provideArtemisTumAetUiTranslator(), { provide: TranslateService, useClass: MockTranslateService }, { provide: DialogService, useClass: MockDialogService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentOverviewTableComponent);
        component = fixture.componentInstance;
    });

    it('should render one row per student and presentation and a message when there are none', () => {
        render([createRow(), exerciseRow()]);
        expect(bodyRows()).toHaveLength(2);

        render([]);
        expect(query('tbody td[colspan="8"]')?.getAttribute('jhiTranslate')).toBe('artemisApp.presentationAssessment.noMatchingStudents');
    });

    it('should show the assessor name and login and a dash without one', () => {
        render([createRow({ instance: { id: 10, resultPoints: 12, assessor: { login: 'tutor1', name: 'Tutor One' } } }), exerciseRow()]);

        const [assessed, unassessed] = Array.from(element().querySelectorAll<HTMLElement>('[data-testid="overview-assessor"]'));
        expect(assessed.querySelector('span')?.textContent?.trim()).toBe('Tutor One');
        expect(assessed.querySelector('small')?.textContent?.trim()).toBe('tutor1');
        expect(unassessed.textContent?.trim()).toBe('-');
    });

    it('should show the result points against the maximum of the row presentation', () => {
        render([createRow(), exerciseRow()]);

        const [standalone, linked] = bodyRows();
        expect(standalone.textContent).toContain('12 / 20');
        expect(linked.textContent).toContain('7 / 10');
    });

    it('should tag rows by whether the presentation is linked to an exercise', () => {
        render([createRow(), exerciseRow()]);

        const [standalone, linked] = bodyRows();
        expect(standalone.textContent).toContain('artemisApp.presentationAssessment.typeStandalone');
        expect(linked.textContent).toContain('artemisApp.presentationAssessment.typeExercise');
        expect(linked.textContent).toContain('Sorting');
    });

    it('should offer the delete button only when deleting is allowed', () => {
        render([createRow()]);
        expect(query('[data-testid="delete-instance-button"]')).toBeNull();

        render([createRow()], true);
        expect(query('[data-testid="delete-instance-button"]')).not.toBeNull();
    });

    it('should emit the whole row when edit is clicked', () => {
        const row = exerciseRow();
        const editSpy = vi.fn();
        component.editInstance.subscribe(editSpy);
        render([row]);

        query('[data-testid="edit-instance-button"] button')!.click();

        expect(editSpy).toHaveBeenCalledWith(row);
    });

    it('should emit the sort field when a sortable column is clicked', () => {
        const sortSpy = vi.fn();
        component.sortChange.subscribe(sortSpy);
        render([createRow()]);

        query('th[tumAetUiSortableColumn="presentationTitle"] button')!.click();

        expect(sortSpy).toHaveBeenCalledWith({ field: 'presentationTitle', order: 1 });
    });

    it('should show and hide the presentation details of a row when the student is clicked', () => {
        render([createRow()]);
        const toggle = query('[data-testid="toggle-row-details"]')!;
        expect(query('.presentation-detail-row')).toBeNull();

        toggle.click();
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        const details = query('#presentation-overview-detail-10\\:student1');
        expect(details?.textContent).toContain('Ten minutes');
        expect(details?.textContent).toContain('Room 1');

        toggle.click();
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect(query('.presentation-detail-row')).toBeNull();
    });
});
