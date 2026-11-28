import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FocusChipComponent } from 'app/metis/shared/focus-chip/focus-chip.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('FocusChipComponent', () => {
    const repositoryPath = 'src/de/tum/in/www1/example/MergeSort.java';

    let fixture: ComponentFixture<FocusChipComponent>;
    let translateService: TranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FocusChipComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(FocusChipComponent);
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** Renders the chip and returns its host element, which is the chip itself. */
    function render(path: string, changedLines?: number): HTMLElement {
        fixture.componentRef.setInput('path', path);
        if (changedLines !== undefined) {
            fixture.componentRef.setInput('changedLines', changedLines);
        }
        fixture.detectChanges();
        return fixture.nativeElement;
    }

    function part(testId: string): HTMLElement {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    function changedLinesText(): HTMLElement {
        return fixture.nativeElement.querySelector('[jhiTranslate="artemisApp.metis.focusChip.changedLines"]');
    }

    it('should show the file name of a repository path and keep the full path as its tooltip', () => {
        const chip = render(repositoryPath, 12);

        expect(chip.getAttribute('data-testid')).toBe('metis-focus-chip');
        expect(part('metis-focus-chip-file').textContent).toBe('MergeSort.java');
        expect(chip.getAttribute('title')).toBe(repositoryPath);
    });

    it('should let screen readers read the full path instead of the shortened name', () => {
        const chip = render(repositoryPath, 12);

        expect(part('metis-focus-chip-path').textContent).toBe(repositoryPath);
        expect(part('metis-focus-chip-path').classList).toContain('sr-only');
        // The hidden path is positioned absolutely; the chip is its containing block, so the path stays inside the chip.
        expect(chip.classList).toContain('relative');
        expect(part('metis-focus-chip-file').getAttribute('aria-hidden')).toBe('true');
    });

    it('should keep the count of changed lines next to the name', () => {
        const getSpy = vi.spyOn(translateService, 'get');

        render(repositoryPath, 12);

        expect(getSpy).toHaveBeenCalledWith('artemisApp.metis.focusChip.changedLines', { count: 12 });
        expect(changedLinesText().textContent).toBe('artemisApp.metis.focusChip.changedLines');
    });

    it('should count no changed lines when the question carries no count', () => {
        const getSpy = vi.spyOn(translateService, 'get');

        render(repositoryPath);

        expect(getSpy).toHaveBeenCalledWith('artemisApp.metis.focusChip.changedLines', { count: 0 });
    });

    it('should let only the file name give way, with an ellipsis, when the chip is too narrow', () => {
        const chip = render(repositoryPath, 12);

        // jsdom applies no CSS, so the test pins the classes the ellipsis needs. The chip is a custom element and thus inline by
        // default; only as a flex container does it turn its parts into flex items, which can shrink and end in an ellipsis.
        expect(chip.classList).toContain('inline-flex');
        expect(chip.classList).toContain('max-w-full');
        expect(chip.classList).toContain('min-w-0');
        expect(part('metis-focus-chip-file').classList).toContain('truncate');
        expect(part('metis-focus-chip-file').classList).toContain('min-w-0');
        expect(changedLinesText().classList).toContain('shrink-0');
        expect(changedLinesText().classList).toContain('whitespace-nowrap');
    });

    it.each([
        ['MergeSort.java', 'MergeSort.java'],
        ['src/MergeSort.java', 'MergeSort.java'],
        ['src/main/', 'src/main/'],
    ])('should show %s as %s', (path, shown) => {
        const chip = render(path);

        expect(part('metis-focus-chip-file').textContent).toBe(shown);
        expect(chip.getAttribute('title')).toBe(path);
    });
});
