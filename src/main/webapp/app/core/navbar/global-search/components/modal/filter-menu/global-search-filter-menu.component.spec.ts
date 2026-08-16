import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { faCube } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { GlobalSearchFilterMenuComponent } from './global-search-filter-menu.component';
import { FilterMenuOption } from '../../../models/search-menu.model';

describe('GlobalSearchFilterMenuComponent', () => {
    let component: GlobalSearchFilterMenuComponent;
    let fixture: ComponentFixture<GlobalSearchFilterMenuComponent>;

    const option = (id: string, value: string): FilterMenuOption => ({ id, label: id, icon: faCube, action: { kind: 'value', value } });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GlobalSearchFilterMenuComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(GlobalSearchFilterMenuComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('options', [option('type:exercise', 'exercise'), option('type:lecture', 'lecture')]);
        fixture.componentRef.setInput('activeIndex', 1);
        fixture.detectChanges();
    });

    it('renders a listbox with each option carrying its shared DOM id', () => {
        const listbox = fixture.nativeElement.querySelector('ul[role="listbox"]');
        expect(listbox.getAttribute('id')).toBe('global-search-filter-menu');
        expect(fixture.nativeElement.querySelector('[id="gs-filter-option-type:exercise"]')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('[id="gs-filter-option-type:lecture"]')).toBeTruthy();
    });

    it('marks the active option as selected', () => {
        const options = fixture.nativeElement.querySelectorAll('li[role="option"]');
        expect(options[1].classList).toContain('is-active');
        expect(options[1].getAttribute('aria-selected')).toBe('true');
        expect(options[0].getAttribute('aria-selected')).toBe('false');
    });

    it('emits optionSelected on click and optionHovered on mouseenter with the index', () => {
        const selected = vi.spyOn(component.optionSelected, 'emit');
        const hovered = vi.spyOn(component.optionHovered, 'emit');
        const options = fixture.nativeElement.querySelectorAll('li[role="option"]');

        options[0].dispatchEvent(new MouseEvent('mouseenter'));
        options[0].click();

        expect(hovered).toHaveBeenCalledWith(0);
        expect(selected).toHaveBeenCalledWith(0);
    });

    it('shows the empty state when there are no options', () => {
        fixture.componentRef.setInput('options', []);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.filter-menu-empty')).toBeTruthy();
        expect(fixture.nativeElement.querySelectorAll('li[role="option"]').length).toBe(0);
    });

    it('renders a hint with a faded {placeholder} and tints the exclude pill red', () => {
        fixture.componentRef.setInput('options', [
            { id: 'exclude', label: 'Exclude a type or course', icon: faCube, hint: '−{filter}:', action: { kind: 'setQuery', query: '-' } },
        ]);
        fixture.detectChanges();

        const pill = fixture.nativeElement.querySelector('.filter-menu-syntax');
        expect(pill.classList).toContain('filter-menu-syntax--ex');
        expect(pill.textContent.replace(/\s/g, '')).toBe('−filter:');
        expect(pill.querySelector('.hint-placeholder').textContent).toBe('filter');
    });

    it('renders a plain hint unchanged (no placeholder span content)', () => {
        fixture.componentRef.setInput('options', [{ id: 'type', label: 'Filter by type', icon: faCube, hint: 'type:', action: { kind: 'operator', prefix: 'type:' } }]);
        fixture.detectChanges();

        const pill = fixture.nativeElement.querySelector('.filter-menu-syntax');
        expect(pill.classList).not.toContain('filter-menu-syntax--ex');
        expect(pill.textContent.replace(/\s/g, '')).toBe('type:');
    });

    it('tints every value glyph red while excluding (a negated operator is active)', () => {
        fixture.componentRef.setInput('options', [option('type:course', 'course'), option('type:exercise', 'exercise')]);
        fixture.componentRef.setInput('exclude', true);
        fixture.detectChanges();

        const glyphs = fixture.nativeElement.querySelectorAll('.filter-menu-glyph');
        expect(glyphs.length).toBe(2);
        glyphs.forEach((glyph: Element) => expect(glyph.classList).toContain('filter-menu-glyph--ex'));
    });

    it('shows a header back button when showBack is true and emits back on click', () => {
        const backSpy = vi.spyOn(component.back, 'emit');
        fixture.componentRef.setInput('showBack', true);
        fixture.detectChanges();

        const backBtn = fixture.nativeElement.querySelector('.filter-menu-back');
        expect(backBtn).toBeTruthy();
        backBtn.click();
        expect(backSpy).toHaveBeenCalled();
    });

    it('hides the back button when showBack is false', () => {
        fixture.componentRef.setInput('showBack', false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.filter-menu-back')).toBeNull();
    });
});
