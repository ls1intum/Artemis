import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisCitationTextComponent } from './iris-citation-text.component';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { provideHttpClient } from '@angular/common/http';
import { escapeHtml, formatCitationLabel, parseCitation, removeCitationBlocks, replaceCitationBlocks, resolveCitationTypeClass } from './iris-citation-text.util';

describe('IrisCitationTextComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisCitationTextComponent>;

    const render = (text: string, citationInfo: IrisCitationMetaDTO[] = []) => {
        fixture.componentRef.setInput('text', text);
        fixture.componentRef.setInput('citationInfo', citationInfo);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    };

    const rect = (left: number, right: number): DOMRect =>
        ({
            left,
            right,
            top: 0,
            bottom: 50,
            width: right - left,
            height: 50,
            x: left,
            y: 0,
            toJSON: () => ({}),
        }) as DOMRect;

    const rectWithVertical = (left: number, right: number, top: number, bottom: number): DOMRect =>
        ({
            left,
            right,
            top,
            bottom,
            width: right - left,
            height: bottom - top,
            x: left,
            y: top,
            toJSON: () => ({}),
        }) as DOMRect;

    const setupTooltip = () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'L', lectureUnitTitle: '' }];
        const el = render('[cite:L:7::::Key:Summary]', citationInfo);
        const citation = el.querySelector('.iris-citation--has-summary') as HTMLElement;
        const summary = citation.querySelector('.iris-citation__summary') as HTMLElement;

        expect(citation).toBeTruthy();
        return { el, citation, summary };
    };

    const mockClosestBoundaries = (citation: HTMLElement, el: HTMLElement) => {
        const originalClosest = citation.closest.bind(citation);
        vi.spyOn(citation, 'closest').mockImplementation((selector: string) => {
            if (selector === '.bubble-left') return el;
            if (selector === 'div.messages') return el;
            if (selector === 'jhi-iris-citation-text') return el;
            return originalClosest(selector);
        });
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [IrisCitationTextComponent],
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(IrisCitationTextComponent);
    });

    it('renders text without citations', () => {
        const el = render('Hello world');
        expect(el.textContent).toContain('Hello world');
    });

    it('renders single citation with summary and lecture info', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'My Lecture', lectureUnitTitle: 'My Unit' }];
        const el = render('Hello [cite:L:7::::Keyword:Summary] world', citationInfo);

        expect(el.querySelector('.iris-citation')).toBeTruthy();
        expect(el.querySelector('.iris-citation__summary-title')?.textContent?.trim()).toBe('My Unit');
        expect(el.querySelector('.iris-citation__summary-text')?.textContent?.trim()).toBe('Summary');
        expect(el.querySelector('.iris-citation__summary-lecture')).toBeTruthy();
    });

    it('uses keyword as summary title when lectureUnitTitle is empty', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'Lecture', lectureUnitTitle: '' }];
        const el = render('[cite:L:7::::MyKeyword:Summary]', citationInfo);

        expect(el.querySelector('.iris-citation__summary-title')?.textContent?.trim()).toBe('MyKeyword');
        expect(el.querySelector('.iris-citation__summary-lecture')).toBeTruthy();
    });

    it('renders group without summary section', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'L1', lectureUnitTitle: '' },
            { entityId: 2, lectureTitle: 'L2', lectureUnitTitle: '' },
        ];
        const el = render('[cite:L:1:::::] [cite:L:2:::::]', citationInfo);

        expect(el.querySelector('.iris-citation-group')).toBeTruthy();
        expect(el.querySelector('.iris-citation-group--has-summary')).toBeFalsy();
        expect(el.querySelector('.iris-citation__summary')).toBeFalsy();
    });

    it('does not render navigation controls when group has only one summary', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'L1', lectureUnitTitle: '' },
            { entityId: 2, lectureTitle: 'L2', lectureUnitTitle: '' },
        ];
        const el = render('[cite:L:1::::One:Summary] [cite:L:2:::::]', citationInfo);

        expect(el.querySelector('.iris-citation-group--has-summary')).toBeTruthy();
        expect(el.querySelector('.iris-citation__summary-item')).toBeTruthy();
        expect(el.querySelector('.iris-citation__nav')).toBeFalsy();
    });

    it('updates bubble when navigating a citation group', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'L1', lectureUnitTitle: '' },
            { entityId: 2, lectureTitle: 'L2', lectureUnitTitle: '' },
        ];
        const el = render('[cite:L:1:5:::One:S1] [cite:F:2::::FAQ:S2]', citationInfo);

        const group = el.querySelector('.iris-citation-group') as HTMLElement;
        const bubble = group.querySelector('.iris-citation') as HTMLElement;
        const bubbleText = bubble.querySelector('.iris-citation__text') as HTMLElement;
        const navButtons = group.querySelectorAll('.iris-citation__nav-button') as NodeListOf<HTMLElement>;

        expect(bubbleText.textContent?.trim()).toBe('One');
        expect(bubble.classList.contains('iris-citation--slide')).toBe(true);

        navButtons[1].click();

        expect(bubbleText.textContent?.trim()).toBe('FAQ');
        expect(bubble.classList.contains('iris-citation--faq')).toBe(true);
        expect(bubble.classList.contains('iris-citation--slide')).toBe(false);

        navButtons[0].click();

        expect(bubbleText.textContent?.trim()).toBe('One');
        expect(bubble.classList.contains('iris-citation--slide')).toBe(true);
    });

    it('adjusts tooltip shift based on overflow', () => {
        const { el, citation, summary } = setupTooltip();

        mockClosestBoundaries(citation, el);

        const boundarySpy = vi.spyOn(el, 'getBoundingClientRect');
        const summarySpy = vi.spyOn(summary, 'getBoundingClientRect');

        const cases = [
            { boundary: rect(0, 200), summary: rect(50, 300), expected: '-100px' },
            { boundary: rect(100, 500), summary: rect(80, 200), expected: '20px' },
            { boundary: rect(0, 400), summary: rect(50, 200), expected: '0px' },
        ];

        cases.forEach(({ boundary, summary: summaryRect, expected }) => {
            boundarySpy.mockReturnValue(boundary);
            summarySpy.mockReturnValue(summaryRect);

            citation.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

            expect(citation.style.getPropertyValue('--iris-citation-shift')).toBe(expected);
        });
    });

    describe('Vertical collision detection', () => {
        it('positions tooltip above when there is sufficient space', () => {
            const { el, citation, summary } = setupTooltip();

            mockClosestBoundaries(citation, el);

            // Vertical boundary top at y=0, summary top at y=82 → summary.top >= boundary.top → fits
            const boundarySpy = vi.spyOn(el, 'getBoundingClientRect').mockReturnValue(rectWithVertical(0, 400, 0, 600));
            const summarySpy = vi.spyOn(summary, 'getBoundingClientRect').mockReturnValue(rectWithVertical(50, 200, 82, 182));

            citation.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

            expect(citation.style.getPropertyValue('--iris-citation-vertical-offset')).toBe('calc(-100% - 18px)');

            boundarySpy.mockRestore();
            summarySpy.mockRestore();
        });

        it('positions tooltip below when it overflows boundary top', () => {
            const { el, citation, summary } = setupTooltip();

            mockClosestBoundaries(citation, el);

            // Vertical boundary top at y=0, summary top at y=-10 → summary.top < boundary.top → overflow
            const boundarySpy = vi.spyOn(el, 'getBoundingClientRect').mockReturnValue(rectWithVertical(0, 400, 0, 600));
            const summarySpy = vi.spyOn(summary, 'getBoundingClientRect').mockReturnValue(rectWithVertical(50, 200, -10, 90));

            citation.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

            expect(citation.style.getPropertyValue('--iris-citation-vertical-offset')).toBe('0px');

            boundarySpy.mockRestore();
            summarySpy.mockRestore();
        });

        it('works correctly with both horizontal and vertical collision', () => {
            const { el, citation, summary } = setupTooltip();

            mockClosestBoundaries(citation, el);

            // Summary overflows both boundaries (left and top)
            const boundarySpy = vi.spyOn(el, 'getBoundingClientRect').mockReturnValue(rectWithVertical(0, 400, 0, 600));
            const citationSpy = vi.spyOn(citation, 'getBoundingClientRect').mockReturnValue(rectWithVertical(10, 60, 40, 60));
            const summarySpy = vi.spyOn(summary, 'getBoundingClientRect').mockReturnValue(rectWithVertical(-105, 45, -78, 22));

            citation.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

            expect(citation.style.getPropertyValue('--iris-citation-vertical-offset')).toBe('0px');
            expect(citation.style.getPropertyValue('--iris-citation-shift')).toBe('105px');

            boundarySpy.mockRestore();
            citationSpy.mockRestore();
            summarySpy.mockRestore();
        });

        it('adds flipped class when positioned below', () => {
            const { el, citation, summary } = setupTooltip();

            mockClosestBoundaries(citation, el);

            const boundarySpy = vi.spyOn(el, 'getBoundingClientRect').mockReturnValue(rectWithVertical(0, 400, 0, 600));
            const summarySpy = vi.spyOn(summary, 'getBoundingClientRect').mockReturnValue(rectWithVertical(50, 200, -10, 90));

            citation.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

            expect(summary.classList.contains('iris-citation__summary--flipped')).toBe(true);

            boundarySpy.mockRestore();
            summarySpy.mockRestore();
        });

        it('removes flipped class when positioned above', () => {
            const { el, citation, summary } = setupTooltip();

            // First add flipped class
            summary.classList.add('iris-citation__summary--flipped');

            mockClosestBoundaries(citation, el);

            const boundarySpy = vi.spyOn(el, 'getBoundingClientRect').mockReturnValue(rectWithVertical(0, 400, 0, 600));
            const summarySpy = vi.spyOn(summary, 'getBoundingClientRect').mockReturnValue(rectWithVertical(50, 200, 82, 182));

            citation.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

            expect(summary.classList.contains('iris-citation__summary--flipped')).toBe(false);

            boundarySpy.mockRestore();
            summarySpy.mockRestore();
        });

        it('removes flipped class on mouseout', () => {
            const { citation, summary } = setupTooltip();
            summary.classList.add('iris-citation__summary--flipped');

            citation.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, relatedTarget: document.body }));

            expect(summary.classList.contains('iris-citation__summary--flipped')).toBe(false);
        });
    });
});

describe('Iris citation util', () => {
    it('parses a citation with a summary containing colons', () => {
        const raw = '[cite:L:42:5:10:20:Key:Summary:with:colon]';
        const parsed = parseCitation(raw);

        expect(parsed).toEqual({
            type: 'L',
            entityId: '42',
            page: '5',
            start: '10',
            end: '20',
            keyword: 'Key',
            summary: 'Summary:with:colon',
        });
    });

    it('resolves citation type classes', () => {
        const cases = [
            [{ type: 'F', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' }, 'iris-citation--faq'],
            [{ type: 'L', entityId: '1', page: '', start: '00:01', end: '', keyword: '', summary: '' }, 'iris-citation--video'],
            [{ type: 'L', entityId: '1', page: '3', start: '', end: '', keyword: '', summary: '' }, 'iris-citation--slide'],
            [{ type: 'L', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' }, 'iris-citation--source'],
        ] as const;

        cases.forEach(([input, expected]) => {
            expect(resolveCitationTypeClass(input)).toBe(expected);
        });
    });

    it('formats citation labels with escaping and fallbacks', () => {
        const cases = [
            [{ type: 'L', entityId: '1', page: '', start: '', end: '', keyword: '<b>Key</b>', summary: '' }, '&lt;b&gt;Key&lt;/b&gt;'],
            [{ type: 'L', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' }, 'Source'],
            [{ type: 'F', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' }, 'FAQ'],
        ] as const;

        cases.forEach(([input, expected]) => {
            expect(formatCitationLabel(input)).toBe(expected);
        });
    });

    it('replaces citations with custom renderers', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1' },
            { entityId: 7, lectureTitle: 'Lecture 7', lectureUnitTitle: 'Unit 7' },
        ];
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');

        const singleText = 'Hello [cite:L:7::::Keyword:Summary] world';
        const groupText = '[cite:L:1::::One:Summary] [cite:L:1::::Two:Summary]';

        const singleResult = replaceCitationBlocks(singleText, citationInfo, { renderSingle, renderGroup });
        const groupResult = replaceCitationBlocks(groupText, citationInfo, { renderSingle, renderGroup });

        expect(singleResult).toContain('<single />');
        expect(groupResult).toContain('<group />');
        expect(renderSingle).toHaveBeenCalled();
        expect(renderGroup).toHaveBeenCalled();
    });

    it('ignores texts without citation markers', () => {
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');
        const text = 'No citations here';

        const result = replaceCitationBlocks(text, [], { renderSingle, renderGroup });

        expect(result).toBe(text);
        expect(renderSingle).not.toHaveBeenCalled();
        expect(renderGroup).not.toHaveBeenCalled();
    });

    it('removes citation blocks from text', () => {
        const text = 'Hello [cite:L:7::::Keyword:Summary] world [cite:F:1::::FAQ:Summary]';
        const result = removeCitationBlocks(text);

        expect(result).toBe('Hello  world');
        expect(result).not.toContain('[cite:');
    });

    it('escapes HTML in raw text', () => {
        expect(escapeHtml('<span>"&"</span>')).toBe('&lt;span&gt;&quot;&amp;&quot;&lt;/span&gt;');
    });
});
