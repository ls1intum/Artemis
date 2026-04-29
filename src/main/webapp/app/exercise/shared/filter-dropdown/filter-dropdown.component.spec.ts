import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FilterDropdownComponent, FilterGroup } from 'app/exercise/shared/filter-dropdown/filter-dropdown.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('FilterDropdownComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FilterDropdownComponent;
    let fixture: ComponentFixture<FilterDropdownComponent>;

    const mockFilters = ['SUCCESSFUL', 'UNSUCCESSFUL'];
    const mockGroupedFilters: FilterGroup[] = [
        { labelKey: 'group.progress', items: ['NotStarted', 'Started', 'Submitted'] },
        { labelKey: 'group.attendance', items: ['AttendanceChecked', 'AttendanceNotChecked'] },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FilterDropdownComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(FilterDropdownComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(FilterDropdownComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('filters', mockFilters);
        fixture.componentRef.setInput('activeFilter', 'All');

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should reflect filters input', () => {
        expect(component.filters()).toEqual(mockFilters);
    });

    it('should reflect activeFilter input', () => {
        expect(component.activeFilter()).toBe('All');
    });

    it('should default allValue to "All"', () => {
        expect(component.allValue()).toBe('All');
    });

    it('should default filterLabelKey to artemisApp.exercise.filter', () => {
        expect(component.filterLabelKey()).toBe('artemisApp.exercise.filter');
    });

    it('should default translationPrefix to artemisApp.exercise.show', () => {
        expect(component.translationPrefix()).toBe('artemisApp.exercise.show');
    });

    it('should reflect groupedFilters input', () => {
        fixture.componentRef.setInput('groupedFilters', mockGroupedFilters);
        expect(component.groupedFilters()).toEqual(mockGroupedFilters);
    });

    it('should reflect custom filterLabelKey', () => {
        fixture.componentRef.setInput('filterLabelKey', 'artemisApp.examManagement.filter');
        expect(component.filterLabelKey()).toBe('artemisApp.examManagement.filter');
    });

    it('should reflect custom translationPrefix', () => {
        fixture.componentRef.setInput('translationPrefix', 'artemisApp.examManagement.examStudents.filter.');
        expect(component.translationPrefix()).toBe('artemisApp.examManagement.examStudents.filter.');
    });

    describe('isFiltered', () => {
        it('should be false when activeFilter equals allValue (default "All")', () => {
            fixture.componentRef.setInput('activeFilter', 'All');
            expect(component.isFiltered()).toBe(false);
        });

        it('should be true when activeFilter differs from allValue', () => {
            fixture.componentRef.setInput('activeFilter', 'SUCCESSFUL');
            expect(component.isFiltered()).toBe(true);
        });

        it('should be false when activeFilter matches custom allValue', () => {
            fixture.componentRef.setInput('allValue', 'NONE');
            fixture.componentRef.setInput('activeFilter', 'NONE');
            expect(component.isFiltered()).toBe(false);
        });

        it('should be true when activeFilter does not match custom allValue', () => {
            fixture.componentRef.setInput('allValue', 'NONE');
            fixture.componentRef.setInput('activeFilter', 'SUCCESSFUL');
            expect(component.isFiltered()).toBe(true);
        });

        it('should be true when a grouped filter item is active', () => {
            fixture.componentRef.setInput('groupedFilters', mockGroupedFilters);
            fixture.componentRef.setInput('activeFilter', 'Submitted');
            expect(component.isFiltered()).toBe(true);
        });
    });

    describe('filterChange output', () => {
        it('should emit when filterChange is triggered', () => {
            const emitSpy = vi.spyOn(component.filterChange, 'emit');

            component.filterChange.emit('SUCCESSFUL');

            expect(emitSpy).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith('SUCCESSFUL');
        });

        it('should emit allValue when resetting filter', () => {
            const emitSpy = vi.spyOn(component.filterChange, 'emit');

            component.filterChange.emit('All');

            expect(emitSpy).toHaveBeenCalledWith('All');
        });
    });
});
