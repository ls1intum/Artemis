import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TumAetUiAutoCompleteComponent } from '@tumaet/ui-angular';
import { firstValueFrom, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { User } from 'app/account/user/user.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { PresentationAssessmentPresenterSelectorComponent } from 'app/presentation/manage/presentation-assessment-presenter-selector.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';

describe('PresentationAssessmentPresenterSelectorComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentPresenterSelectorComponent>;
    let component: PresentationAssessmentPresenterSelectorComponent;
    let searchStudents: ReturnType<typeof vi.fn>;

    const assignedPresenter = Object.assign(new User(), { id: 1, login: 'alice', name: 'Alice' });

    beforeEach(async () => {
        searchStudents = vi.fn().mockReturnValue(of(new HttpResponse<User[]>({ body: [] })));

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentPresenterSelectorComponent],
            providers: [
                { provide: CourseManagementService, useValue: { searchStudents } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: AlertService, useValue: { error: vi.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentPresenterSelectorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('presenters', [assignedPresenter]);
        fixture.detectChanges();
    });

    it('should not search for terms shorter than three characters', async () => {
        const result = await firstValueFrom(component.searchPresenters(of('ab')));

        expect(result).toEqual([]);
        expect(searchStudents).not.toHaveBeenCalled();
    });

    it('should search the first 25 students and omit already assigned presenters', async () => {
        const availablePresenter = Object.assign(new User(), { id: 2, login: 'bob', name: 'Bob' });
        searchStudents.mockReturnValue(
            of(
                new HttpResponse<User[]>({
                    body: [assignedPresenter, availablePresenter],
                }),
            ),
        );

        const result = await firstValueFrom(component.searchPresenters(of('alice')));

        expect(searchStudents).toHaveBeenCalledWith(7, 'alice', 0, 25);
        expect(result).toEqual([availablePresenter]);
    });

    it('should provide presenter suggestions with name and login', async () => {
        const presenter = Object.assign(new User(), { id: 2, login: 'bob', name: 'Bob' });
        searchStudents.mockReturnValue(of(new HttpResponse<User[]>({ body: [presenter] })));

        const autocomplete = fixture.debugElement.query(By.directive(TumAetUiAutoCompleteComponent)).componentInstance as TumAetUiAutoCompleteComponent;

        autocomplete.searchRequested.emit({ query: 'bob' });

        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(autocomplete.suggestions()).toEqual([{ presenter, label: 'Bob (bob)' }]);
        });

        expect(searchStudents).toHaveBeenCalledWith(7, 'bob', 0, 25);
    });

    it('should add the selected suggestion and clear the autocomplete', async () => {
        const presenter = Object.assign(new User(), { id: 2, login: 'bob', name: 'Bob' });
        searchStudents.mockReturnValue(of(new HttpResponse<User[]>({ body: [presenter] })));

        const autocomplete = fixture.debugElement.query(By.directive(TumAetUiAutoCompleteComponent)).componentInstance as TumAetUiAutoCompleteComponent;
        const writeValue = vi.spyOn(autocomplete, 'writeValue');

        autocomplete.searchRequested.emit({ query: 'bob' });

        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(autocomplete.suggestions()).toHaveLength(1);
        });

        autocomplete.optionSelected.emit({ value: autocomplete.suggestions()[0] });

        expect(component.presenters()).toEqual([assignedPresenter, presenter]);
        expect(writeValue).toHaveBeenCalledWith(undefined);
    });

    it('should add only presenters that are not assigned yet', () => {
        const availablePresenter = Object.assign(new User(), { id: 2, login: 'bob', name: 'Bob' });

        component.selectPresenter(assignedPresenter);
        component.selectPresenter(availablePresenter);

        expect(component.presenters().map((presenter) => presenter.login)).toEqual(['alice', 'bob']);
    });

    it('should display presenters sorted by name without changing the selection', () => {
        const bob = Object.assign(new User(), { id: 2, login: 'bob', name: 'Bob' });
        fixture.componentRef.setInput('presenters', [bob, assignedPresenter]);
        fixture.detectChanges();

        const rows = fixture.debugElement.queryAll(By.css('tbody tr'));
        const names = rows.map((row) => row.nativeElement.cells[2].textContent.trim());

        expect(names).toEqual(['Alice', 'Bob']);
        expect(component.presenters()).toEqual([bob, assignedPresenter]);
    });

    it('should toggle login sorting when the column header is clicked', () => {
        const bob = Object.assign(new User(), { id: 2, login: 'bob', name: 'Aaron' });
        fixture.componentRef.setInput('presenters', [assignedPresenter, bob]);
        fixture.detectChanges();

        const loginHeader = fixture.debugElement.query(By.css('th[tumAetUiSortableColumn="login"]'));
        const button = loginHeader.query(By.css('button'));

        button.nativeElement.click();
        fixture.detectChanges();

        const displayedLogins = () => fixture.debugElement.queryAll(By.css('tbody tr')).map((row) => row.nativeElement.cells[1].textContent.trim());

        expect(displayedLogins()).toEqual(['alice', 'bob']);
        expect(loginHeader.nativeElement.getAttribute('aria-sort')).toBe('ascending');

        button.nativeElement.click();
        fixture.detectChanges();

        expect(displayedLogins()).toEqual(['bob', 'alice']);
        expect(loginHeader.nativeElement.getAttribute('aria-sort')).toBe('descending');
    });

    it('should remove a presenter locally', () => {
        component.removePresenter(assignedPresenter);

        expect(component.presenters()).toEqual([]);
    });
});
