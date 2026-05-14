import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TutorialRegistrationsStudentsTableComponent } from './tutorial-registrations-students-table.component';
import { TutorialRegistrationsStudentsTableRemoveActionColumnInfo } from './tutorial-registrations-students-table.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

describe('TutorialRegistrationsStudentsTable', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsStudentsTableComponent;
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
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render one table row per input student', () => {
        fixture.componentRef.setInput('students', [firstStudent, secondStudent]);
        fixture.detectChanges();

        const rows = fixture.nativeElement.querySelectorAll('.table-row');

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
        expect(fixture.nativeElement.querySelector('.remove-button')).toBeNull();
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
        expect(fixture.nativeElement.querySelector('.remove-button')).not.toBeNull();
        expect(component).toBeTruthy();
    });
});
