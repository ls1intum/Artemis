import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
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

    it('should render one row per student and a message when there are none', () => {
        render([createRow(), createRow({ studentLogin: 'student2', rowKey: '11:student2', displayName: 'Student Two' })]);
        expect(element().querySelectorAll('tbody tr')).toHaveLength(2);

        render([]);
        expect(query('tbody td[colspan="6"]')?.getAttribute('jhiTranslate')).toBe('artemisApp.presentationAssessment.noMatchingStudents');
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

        expect(editSpy).toHaveBeenCalledWith(row.instance);
    });

    it('should show and hide the details of a row when the student is clicked', () => {
        render([createRow()]);
        const toggle = query('button[aria-controls="presentation-detail-10:student1"]')!;
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
});
