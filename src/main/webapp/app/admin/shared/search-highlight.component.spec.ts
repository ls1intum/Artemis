import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { SearchHighlightComponent } from 'app/admin/shared/search-highlight.component';

describe('SearchHighlightComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SearchHighlightComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [SearchHighlightComponent] }).compileComponents();
        fixture = TestBed.createComponent(SearchHighlightComponent);
    });

    function textContent(): string {
        return (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
    }

    function highlightedParts(): string[] {
        return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('span.search-highlight')).map((el) => el.textContent ?? '');
    }

    it('renders the full text with no highlight when the term is empty', () => {
        fixture.componentRef.setInput('result', 'admin@tum.de');
        fixture.componentRef.setInput('term', '');
        fixture.detectChanges();

        expect(textContent()).toBe('admin@tum.de');
        expect(highlightedParts()).toHaveLength(0);
    });

    it('highlights a single case-insensitive match while preserving the original casing', () => {
        fixture.componentRef.setInput('result', 'Administrator');
        fixture.componentRef.setInput('term', 'admin');
        fixture.detectChanges();

        expect(textContent()).toBe('Administrator');
        expect(highlightedParts()).toEqual(['Admin']);
    });

    it('highlights every occurrence of the term', () => {
        fixture.componentRef.setInput('result', 'ababa');
        fixture.componentRef.setInput('term', 'a');
        fixture.detectChanges();

        expect(highlightedParts()).toEqual(['a', 'a', 'a']);
        expect(textContent()).toBe('ababa');
    });

    it('coerces non-string results and tolerates undefined', () => {
        fixture.componentRef.setInput('result', 12345);
        fixture.componentRef.setInput('term', '23');
        fixture.detectChanges();
        expect(highlightedParts()).toEqual(['23']);

        fixture.componentRef.setInput('result', undefined);
        fixture.componentRef.setInput('term', 'x');
        fixture.detectChanges();
        expect(textContent()).toBe('');
    });

    it('renders the text unchanged when the term does not occur', () => {
        fixture.componentRef.setInput('result', 'student');
        fixture.componentRef.setInput('term', 'zzz');
        fixture.detectChanges();

        expect(textContent()).toBe('student');
        expect(highlightedParts()).toHaveLength(0);
    });
});
