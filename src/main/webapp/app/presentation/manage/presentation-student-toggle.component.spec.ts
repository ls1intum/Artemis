import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { User } from 'app/account/user/user.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { PresentationStudentToggleComponent } from 'app/presentation/manage/presentation-student-toggle.component';
import { PresentationStudentTableRow } from 'app/presentation/manage/presentation-assessment-management.helper';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationStudentToggleComponent', () => {
    let fixture: ComponentFixture<PresentationStudentToggleComponent>;

    function createRow(email?: string): PresentationStudentTableRow {
        return {
            studentLogin: 'student1',
            student: hydrate(new User(), { login: 'student1', name: 'Student One', email }),
            presentationAssessment: { id: 1, title: 'Final', maxPoints: 20 },
            instance: { id: 10 },
            rowKey: '10:student1',
            displayName: 'Student One',
            assessed: false,
        };
    }

    const button = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="toggle-row-details"]')!;

    function render(row: PresentationStudentTableRow, expanded: boolean) {
        fixture.componentRef.setInput('row', row);
        fixture.componentRef.setInput('expanded', expanded);
        fixture.componentRef.setInput('detailsId', 'details-10');
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationStudentToggleComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationStudentToggleComponent);
    });

    it('should show the name with the email, or the login without one', () => {
        render(createRow('student1@example.org'), false);
        expect(button().textContent).toContain('Student One');
        expect(button().textContent).toContain('student1@example.org');

        render(createRow(), false);
        expect(button().textContent).toContain('student1');
    });

    it('should tell assistive technology which details it controls and whether they are shown', () => {
        render(createRow(), false);
        expect(button().getAttribute('aria-controls')).toBe('details-10');
        expect(button().getAttribute('aria-expanded')).toBe('false');
        expect(button().getAttribute('aria-label')).toBe('artemisApp.presentationAssessment.showDetails');

        render(createRow(), true);
        expect(button().getAttribute('aria-expanded')).toBe('true');
        expect(button().getAttribute('aria-label')).toBe('artemisApp.presentationAssessment.hideDetails');
    });

    it('should emit when clicked', () => {
        const toggledSpy = vi.fn();
        fixture.componentInstance.toggled.subscribe(toggledSpy);
        render(createRow(), false);

        button().click();

        expect(toggledSpy).toHaveBeenCalledOnce();
    });
});
