import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FilterDropdownComponent } from './filter-dropdown.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

/** Exercise the real dropdowns and their portaled options, including changes while a panel remains open. */
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

    function openGroup(index: number): HTMLElement {
        const trigger = fixture.nativeElement.querySelectorAll('[role="combobox"]')[index] as HTMLElement;
        trigger.click();
        fixture.detectChanges();
        return trigger;
    }

    function toggleOption(index: number): void {
        (document.querySelectorAll('[role="option"]')[index] as HTMLElement).click();
        fixture.detectChanges();
    }

    it('keeps the panel open for multiple selections and retains other categories', async () => {
        fixture.componentRef.setInput('activeFilters', ['Manual']);
        fixture.detectChanges();
        await fixture.whenStable();
        const trigger = openGroup(1);
        toggleOption(0);
        toggleOption(1);
        await fixture.whenStable();

        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Successful', 'Unsuccessful']);
        expect(trigger.getAttribute('aria-expanded')).toBe('true');
        expect(document.querySelectorAll('[role="option"][aria-selected="true"]')).toHaveLength(2);
        expect(fixture.nativeElement.querySelectorAll('tumaet-ui-chip')).toHaveLength(3);
        toggleOption(0);
        expect(changed).toHaveBeenLastCalledWith(['Manual', 'Unsuccessful']);
    });

    it('removes a chip without opening its dropdown and can reset all filters', async () => {
        fixture.componentRef.setInput('activeFilters', ['Successful', 'Manual']);
        fixture.detectChanges();
        await fixture.whenStable();
        (fixture.nativeElement.querySelector('tumaet-ui-chip button') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Manual']);
        expect(fixture.nativeElement.querySelector('[aria-expanded="true"]')).toBeNull();

        (fixture.nativeElement.querySelector('button[tumAetUiButton]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith([]);
        expect(fixture.nativeElement.querySelector('button[tumAetUiButton]')).toBeNull();
    });

    it('stays open on scroll and input refresh, and closes on outside click', () => {
        const trigger = openGroup(0);
        fixture.nativeElement.dispatchEvent(new Event('scroll', { bubbles: true }));
        fixture.componentRef.setInput('groupedFilters', [
            { labelKey: 'Submissions', items: ['BuildFailed', 'NoSubmissions'] },
            { labelKey: 'Results', items: ['Successful', 'Unsuccessful'] },
        ]);
        fixture.detectChanges();
        expect(trigger.getAttribute('aria-expanded')).toBe('true');
        document.body.click();
        fixture.detectChanges();
        expect(trigger.getAttribute('aria-expanded')).toBe('false');
    });

    it('renders single-option groups as labeled checkboxes and combines them with dropdown selections', async () => {
        fixture.componentRef.setInput('groupedFilters', [
            { labelKey: 'Results', items: ['Successful', 'Unsuccessful'] },
            { labelKey: 'Mode', items: ['NoPracticeMode'] },
            { labelKey: 'Assessment status', items: ['Locked'] },
        ]);
        fixture.componentRef.setInput('activeFilters', ['Successful', 'Locked']);
        fixture.detectChanges();
        await fixture.whenStable();

        const checkboxes: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
        expect(fixture.nativeElement.querySelectorAll('[role="combobox"]')).toHaveLength(1);
        expect(checkboxes).toHaveLength(2);
        expect(checkboxes[0].checked).toBe(false);
        expect(checkboxes[1].checked).toBe(true);
        expect(checkboxes[0].labels?.[0].textContent).toContain('artemisApp.exercise.showNoPracticeMode');
        (checkboxes[0].labels![0] as HTMLLabelElement).click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Successful', 'Locked', 'NoPracticeMode']);

        checkboxes[1].click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith(['Successful', 'NoPracticeMode']);
        (fixture.nativeElement.querySelector('button[tumAetUiButton]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith([]);
        expect(Array.from(checkboxes, (checkbox) => checkbox.checked)).toEqual([false, false]);
    });

    it('supports keyboard selection and Escape', () => {
        const trigger = fixture.nativeElement.querySelector('[role="combobox"]') as HTMLElement;
        for (const [key, keyCode] of [
            ['Enter', 13],
            ['ArrowDown', 40],
            [' ', 32],
        ] as const) {
            trigger.dispatchEvent(new KeyboardEvent('keydown', { key, keyCode, bubbles: true }));
            fixture.detectChanges();
        }
        expect(changed).toHaveBeenLastCalledWith(['NoSubmissions']);
        trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(trigger.getAttribute('aria-expanded')).toBe('false');
    });
});
