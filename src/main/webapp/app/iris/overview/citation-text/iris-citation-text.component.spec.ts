import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisCitationTextComponent } from './iris-citation-text.component';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { provideHttpClient } from '@angular/common/http';
import { escapeHtml, formatCitationLabel, isCitationType, parseCitation, removeCitationBlocks, replaceCitationBlocks, resolveCitationTypeClass } from './iris-citation-text.util';
import { CITATION_REGEX } from './iris-citation-text.model';

describe('IrisCitationTextComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisCitationTextComponent;
    let fixture: ComponentFixture<IrisCitationTextComponent>;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [IrisCitationTextComponent],
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(IrisCitationTextComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render text without citations', () => {
        fixture.componentRef.setInput('text', 'Hello world');
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement as HTMLElement;
        expect(nativeElement.textContent).toContain('Hello world');
    });

    it('should render text with single citation', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'Lecture', lectureUnitTitle: 'Unit' }];
        fixture.componentRef.setInput('text', 'Hello [cite:L:7:::::Keyword:Summary] world');
        fixture.componentRef.setInput('citationInfo', citationInfo);
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement as HTMLElement;
        expect(nativeElement.querySelector('.iris-citation')).toBeTruthy();
        expect(nativeElement.textContent).toContain('Keyword');
    });

    it('should render grouped citations', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1' },
            { entityId: 2, lectureTitle: 'Lecture 2', lectureUnitTitle: 'Unit 2' },
        ];
        fixture.componentRef.setInput('text', '[cite:L:1:::::One:Summary] [cite:L:2:::::Two:Summary]');
        fixture.componentRef.setInput('citationInfo', citationInfo);
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement as HTMLElement;
        expect(nativeElement.querySelector('.iris-citation-group')).toBeTruthy();
        expect(nativeElement.querySelector('.iris-citation__count')).toBeTruthy();
        expect(nativeElement.textContent).toContain('+1');
    });

    it('should apply correct type classes', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 1, lectureTitle: 'Lecture', lectureUnitTitle: 'Unit' }];
        fixture.componentRef.setInput('text', '[cite:F:1:::::FAQ:Summary]');
        fixture.componentRef.setInput('citationInfo', citationInfo);
        fixture.detectChanges();

        const nativeElement = fixture.nativeElement as HTMLElement;
        expect(nativeElement.querySelector('.iris-citation--faq')).toBeTruthy();
    });

    it('should reactively update when text changes', () => {
        fixture.componentRef.setInput('text', 'First text');
        fixture.detectChanges();

        let nativeElement = fixture.nativeElement as HTMLElement;
        expect(nativeElement.textContent).toContain('First text');

        fixture.componentRef.setInput('text', 'Second text');
        fixture.detectChanges();

        nativeElement = fixture.nativeElement as HTMLElement;
        expect(nativeElement.textContent).toContain('Second text');
        expect(nativeElement.textContent).not.toContain('First text');
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

    it('returns undefined for invalid citations', () => {
        expect(parseCitation('[cite:X:1:::::]')).toBeUndefined(); // Invalid type
        expect(parseCitation('[cite:L::::::]')).toBeUndefined(); // Empty entity ID
        expect(parseCitation('[cite:]')).toBeUndefined(); // Empty
        expect(parseCitation('[cite:L:1]')).toBeUndefined(); // Incomplete
        expect(parseCitation('[cite:L:1::]')).toBeUndefined(); // Incomplete
    });

    it('validates citation types', () => {
        expect(isCitationType('L')).toBe(true);
        expect(isCitationType('F')).toBe(true);
        expect(isCitationType('X')).toBe(false);
        expect(isCitationType(undefined)).toBe(false);
    });

    it('resolves citation type classes', () => {
        expect(resolveCitationTypeClass({ type: 'F', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' })).toBe('iris-citation--faq');
        expect(resolveCitationTypeClass({ type: 'L', entityId: '1', page: '', start: '00:01', end: '', keyword: '', summary: '' })).toBe('iris-citation--video');
        expect(resolveCitationTypeClass({ type: 'L', entityId: '1', page: '3', start: '', end: '', keyword: '', summary: '' })).toBe('iris-citation--slide');
        expect(resolveCitationTypeClass({ type: 'L', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' })).toBe('iris-citation--source');
    });

    it('formats citation labels with escaping and fallbacks', () => {
        const withKeyword = formatCitationLabel({ type: 'L', entityId: '1', page: '', start: '', end: '', keyword: '<b>Key</b>', summary: '' });
        const withoutKeyword = formatCitationLabel({ type: 'L', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' });
        const faqFallback = formatCitationLabel({ type: 'F', entityId: '1', page: '', start: '', end: '', keyword: '', summary: '' });

        expect(withKeyword).toBe('&lt;b&gt;Key&lt;/b&gt;');
        expect(withoutKeyword).toBe('Source');
        expect(faqFallback).toBe('FAQ');
    });

    it('escapes HTML in raw text', () => {
        expect(escapeHtml('<span>"&"</span>')).toBe('&lt;span&gt;&quot;&amp;&quot;&lt;/span&gt;');
    });

    it('replaces single citations with custom renderers', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'Lecture', lectureUnitTitle: 'Unit' }];
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');
        const text = 'Hello [cite:L:7:::::Keyword:Summary] world';

        const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

        expect(result).toContain('<single />');
        expect(renderSingle).toHaveBeenCalledOnce();
        expect(renderGroup).not.toHaveBeenCalled();
    });

    it('groups adjacent citations and calls renderGroup', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1' },
            { entityId: 2, lectureTitle: 'Lecture 2', lectureUnitTitle: 'Unit 2' },
        ];
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');
        const text = '[cite:L:1:::::One:Summary] [cite:L:2:::::Two:Summary]';

        const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

        expect(result).toContain('<group />');
        expect(renderGroup).toHaveBeenCalledOnce();
        expect(renderSingle).not.toHaveBeenCalled();
    });

    it('renders valid citations and leaves invalid citation-like strings as plain text (no group formed)', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1' }];
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');
        const text = 'A [cite:L:1:::::One:Summary] [cite:X:bad:::::] B';

        const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

        expect(renderGroup).not.toHaveBeenCalled();
        expect(renderSingle).toHaveBeenCalledOnce(); // The valid citation gets rendered
        expect(result).toContain('<single />');
        expect(result).toContain('[cite:X:bad:::::]'); // Invalid type stays as-is
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
        const text = 'Hello [cite:L:7:::::Keyword:Summary] world [cite:F:1:::::FAQ:Summary]';
        const result = removeCitationBlocks(text);

        expect(result).toBe('Hello  world');
        expect(result).not.toContain('[cite:');
    });

    it('handles empty text when removing citations', () => {
        expect(removeCitationBlocks('')).toBe('');
        expect(removeCitationBlocks(null as any)).toBe(null);
    });
});

describe('Iris citation util - strict format validation', () => {
    describe('parseCitation with complete format', () => {
        it('should accept complete citations with all empty fields', () => {
            const parsed = parseCitation('[cite:L:5:::::]');
            expect(parsed).toEqual({
                type: 'L',
                entityId: '5',
                page: '',
                start: '',
                end: '',
                keyword: '',
                summary: '',
            });
        });

        it('should handle summaries with colons correctly', () => {
            const parsed = parseCitation('[cite:L:42:5:10:20:Key:Summary:with:colons]');
            expect(parsed).toEqual({
                type: 'L',
                entityId: '42',
                page: '5',
                start: '10',
                end: '20',
                keyword: 'Key',
                summary: 'Summary:with:colons',
            });
        });
    });

    describe('parseCitation rejecting incomplete formats', () => {
        it('should reject citations with too few colons', () => {
            expect(parseCitation('[cite:L:5]')).toBeUndefined();
            expect(parseCitation('[cite:L:5:]')).toBeUndefined();
            expect(parseCitation('[cite:L:5::]')).toBeUndefined();
            expect(parseCitation('[cite:L:5:::]')).toBeUndefined();
            expect(parseCitation('[cite:L:5::::]')).toBeUndefined();
            expect(parseCitation('[cite:L:5:::::]')).toBeDefined(); // 7 colons - valid!
            expect(parseCitation('[cite:L:5::::::]')).toBeDefined(); // 8 colons - also valid (summary is ":")
        });

        it('should reject invalid types', () => {
            expect(parseCitation('[cite:X:1:::::]')).toBeUndefined();
            expect(parseCitation('[cite:l:1:::::]')).toBeUndefined(); // lowercase
        });

        it('should accept non-numeric entity IDs', () => {
            const parsed = parseCitation('[cite:L:abc-123:::::]');
            expect(parsed).toEqual({
                type: 'L',
                entityId: 'abc-123',
                page: '',
                start: '',
                end: '',
                keyword: '',
                summary: '',
            });
        });

        it('should reject empty entity IDs', () => {
            expect(parseCitation('[cite:L::::::]')).toBeUndefined(); // Empty entityId
        });
    });

    describe('CITATION_REGEX with strict matching', () => {
        it('should not match incomplete citations', () => {
            const text = 'Text [cite:L:5] [cite:L:5::] [cite:L:5::::]  more text';
            const matches = text.match(CITATION_REGEX);
            expect(matches).toBeNull();
        });

        it('should match only complete citations', () => {
            const text = 'Valid [cite:L:1:::::K:S] invalid [cite:L:2::] valid [cite:F:3:::::]';
            const matches = text.match(CITATION_REGEX);
            expect(matches).toHaveLength(2);
            expect(matches![0]).toBe('[cite:L:1:::::K:S]');
            expect(matches![1]).toBe('[cite:F:3:::::]');
        });
    });

    describe('replaceCitationBlocks with strict validation', () => {
        it('should ignore incomplete citations', () => {
            const citationInfo: IrisCitationMetaDTO[] = [];
            const renderSingle = vi.fn().mockReturnValue('<single />');
            const renderGroup = vi.fn().mockReturnValue('<group />');

            const text = 'Text [cite:L:5] and [cite:L:7::] more';
            const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

            expect(result).toBe(text); // Returns as-is
            expect(renderSingle).not.toHaveBeenCalled();
        });

        it('should process only complete citations in mixed content', () => {
            const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1' }];
            const renderSingle = vi.fn().mockReturnValue('<rendered>');
            const renderGroup = vi.fn().mockReturnValue('<group>');

            const text = 'Valid [cite:L:1:::::K:S] but [cite:L:5] invalid';
            const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

            expect(result).toContain('<rendered>');
            expect(result).toContain('[cite:L:5]'); // Incomplete stays as-is
            expect(renderSingle).toHaveBeenCalledOnce();
        });
    });
});
