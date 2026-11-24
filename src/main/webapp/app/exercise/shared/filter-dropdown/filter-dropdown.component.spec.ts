import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FilterDropdownComponent } from './filter-dropdown.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

/** Exercise the real button and its portaled popover, including changes while the panel remains open. */
describe('Participation filter toolbar', () => {
    let fixture: ComponentFixture<FilterDropdownComponent>;
    let changed: ReturnType<typeof vi.fn<(values: string[]) => void>>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FilterDropdownComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(FilterDropdownComponent);
        fixture.componentRef.setInput('groupedFilters', [
            { labelKey: 'Submissions', items: ['BuildFailed', 'NoSubmissions'] },
            { labelKey: 'Results', items: ['Successful', 'Unsuccessful'] },
            { labelKey: 'Assessment', items: ['Manual', 'Automatic'] },
        ]);
        fixture.componentRef.setInput('activeFilters', []);
        changed = vi.fn((values: string[]) => fixture.componentRef.setInput('activeFilters', values));
        fixture.componentInstance.filterChange.subscribe(changed);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function filterButton(from: ComponentFixture<FilterDropdownComponent> = fixture): HTMLButtonElement {
        return from.nativeElement.querySelector('[data-testid="filter-dropdown-button"]') as HTMLButtonElement;
    }

    async function openPanel(from: ComponentFixture<FilterDropdownComponent> = fixture): Promise<void> {
        filterButton(from).click();
        from.detectChanges();
        await from.whenStable();
        from.detectChanges();
    }

    function panel(): HTMLElement {
        return document.querySelector('.cdk-overlay-container fieldset') as HTMLElement;
    }

    function resetButton(): HTMLButtonElement {
        return document.querySelector('[data-testid="filter-dropdown-reset"]') as HTMLButtonElement;
    }

    it('is one button that keeps the toolbar at its height, and the groups only exist once the panel is open', async () => {
        expect(fixture.nativeElement.querySelectorAll('button')).toHaveLength(1);
        expect(fixture.nativeElement.querySelectorAll('input, tumaet-ui-checkbox, tumaet-ui-multi-select')).toHaveLength(0);
        expect(panel()).toBeNull();

        await openPanel();

        expect(panel()).not.toBeNull();
        expect(filterButton().getAttribute('aria-expanded')).toBe('true');
    });

    it('shows the number of active filters on the button and in its accessible name', async () => {
        expect(fixture.nativeElement.querySelector('[data-testid="filter-dropdown-count"]')).toBeNull();
        expect(filterButton().getAttribute('aria-label')).toBe('artemisApp.exercise.filter');

        fixture.componentRef.setInput('activeFilters', ['BuildFailed', 'NoSubmissions', 'Manual']);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="filter-dropdown-count"]').textContent).toContain('3');
        expect(filterButton().getAttribute('aria-label')).toBe('artemisApp.exercise.filter (3)');
    });

    it('lists the items of a group as labeled checkboxes and keeps the other categories when one is toggled', async () => {
        fixture.componentRef.setInput('activeFilters', ['Manual']);
        fixture.detectChanges();
        await openPanel();

        const checkboxes = Array.from(panel().querySelectorAll('input[type="checkbox"]')) as HTMLInputElement[];
        expect(checkboxes).toHaveLength(6);
        expect(checkboxes[0].labels?.[0].textContent).toContain('artemisApp.exercise.showBuildFailed');
        expect(checkboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.labels?.[0].textContent?.trim())).toEqual(['artemisApp.exercise.showManual']);

        // the panel stays open for several selections
        checkboxes[2].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Successful']);
        checkboxes[3].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Successful', 'Unsuccessful']);
        expect(filterButton().getAttribute('aria-expanded')).toBe('true');

        checkboxes[2].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Unsuccessful']);
    });

    it('gives checkboxes unique ids across toolbar instances and keeps each label associated', async () => {
        const groups = [{ labelKey: 'Practice', items: ['NoPracticeMode'] }];
        fixture.componentRef.setInput('groupedFilters', groups);
        fixture.detectChanges();
        const second = TestBed.createComponent(FilterDropdownComponent);
        try {
            second.componentRef.setInput('groupedFilters', groups);
            second.componentRef.setInput('activeFilters', []);
            second.detectChanges();
            await second.whenStable();
            const secondChanged = vi.fn();
            second.componentInstance.filterChange.subscribe(secondChanged);

            await openPanel(second);
            const secondInput = document.querySelector('.cdk-overlay-container input[type="checkbox"]') as HTMLInputElement;
            expect(secondInput.labels?.[0].textContent).toContain('artemisApp.exercise.showNoPracticeMode');
            secondInput.labels![0].click();
            second.detectChanges();
            expect(secondChanged).toHaveBeenCalledWith(['NoPracticeMode']);
            expect(changed).not.toHaveBeenCalled();

            // a second panel open at the same time must not reuse the id of the first one
            await openPanel();
            const ids = Array.from(document.querySelectorAll('.cdk-overlay-container input[type="checkbox"]')).map((checkbox) => checkbox.id);
            expect(ids).toHaveLength(2);
            expect(new Set(ids).size).toBe(2);
        } finally {
            second.destroy();
        }
    });

    it('combines segmented results, toggles each independently, and clears them with All', async () => {
        fixture.componentRef.setInput('groupedFilters', [
            { labelKey: 'Results', items: ['Successful', 'Unsuccessful'], segmented: true },
            { labelKey: 'Assessment', items: ['Manual', 'Automatic'] },
        ]);
        fixture.componentRef.setInput('activeFilters', ['Manual', 'Automatic']);
        fixture.detectChanges();
        await openPanel();

        const buttons = panel().querySelectorAll('tumaet-ui-button-group button') as NodeListOf<HTMLButtonElement>;
        expect(buttons).toHaveLength(3);
        expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
        buttons[1].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Automatic', 'Successful']);
        buttons[2].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Automatic', 'Successful', 'Unsuccessful']);
        expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
        expect(buttons[2].getAttribute('aria-pressed')).toBe('true');
        expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
        buttons[1].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Automatic', 'Unsuccessful']);
        expect(buttons[1].getAttribute('aria-pressed')).toBe('false');
        buttons[0].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Automatic']);
    });

    it('renders single-option groups as labeled checkboxes after the groups and combines them with the other selections', async () => {
        fixture.componentRef.setInput('groupedFilters', [
            { labelKey: 'Locked', items: ['Locked'] },
            { labelKey: 'Results', items: ['Successful', 'Unsuccessful'] },
            { labelKey: 'Mode', items: ['NoPracticeMode'] },
        ]);
        fixture.componentRef.setInput('activeFilters', ['Successful', 'Locked']);
        fixture.detectChanges();
        await openPanel();

        const checkboxes = Array.from(panel().querySelectorAll('input[type="checkbox"]')) as HTMLInputElement[];
        // the two items of the group first, then the single checkboxes
        expect(checkboxes.map((checkbox) => checkbox.labels?.[0].textContent?.trim())).toEqual([
            'artemisApp.exercise.showSuccessful',
            'artemisApp.exercise.showUnsuccessful',
            'artemisApp.exercise.showLocked',
            'artemisApp.exercise.showNoPracticeMode',
        ]);
        expect(checkboxes.map((checkbox) => checkbox.checked)).toEqual([true, false, true, false]);

        checkboxes[3].labels![0].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Successful', 'Locked', 'NoPracticeMode']);
        checkboxes[2].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Successful', 'NoPracticeMode']);
    });

    it('resets all categories, and the reset is disabled while nothing is selected', async () => {
        await openPanel();
        expect(resetButton().getAttribute('aria-disabled')).toBe('true');
        resetButton().click();
        fixture.detectChanges();
        expect(changed).not.toHaveBeenCalled();

        fixture.componentRef.setInput('activeFilters', ['BuildFailed', 'NoSubmissions', 'Manual']);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(resetButton().getAttribute('aria-disabled')).toBe('false');
        resetButton().click();
        fixture.detectChanges();

        expect(changed).toHaveBeenLastCalledWith([]);
        expect(fixture.nativeElement.querySelector('[data-testid="filter-dropdown-count"]')).toBeNull();
        expect(Array.from(panel().querySelectorAll('input[type="checkbox"]')).every((checkbox) => !(checkbox as HTMLInputElement).checked)).toBe(true);
    });

    it('stays open when the groups are refreshed and closes with Escape', async () => {
        await openPanel();
        fixture.componentRef.setInput('groupedFilters', [
            { labelKey: 'Submissions', items: ['BuildFailed', 'NoSubmissions'] },
            { labelKey: 'Results', items: ['Successful', 'Unsuccessful'] },
        ]);
        fixture.detectChanges();
        expect(filterButton().getAttribute('aria-expanded')).toBe('true');

        panel().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        await fixture.whenStable();
        expect(filterButton().getAttribute('aria-expanded')).toBe('false');
    });
});
