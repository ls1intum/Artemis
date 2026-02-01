import { describe, expect, it, vi } from 'vitest';
import {
    CITATION_REGEX,
    escapeHtml,
    formatCitationLabel,
    isCitationType,
    parseCitation,
    replaceCitationBlocks,
    resolveCitationTypeClass,
} from 'app/iris/overview/shared/iris-citation.util';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

describe('Iris citation util', () => {
    it('parses a citation with a summary containing colons', () => {
        const raw = '[cite:L:42:5:10:20:Key:Summary:with:colon]';
        const parsed = parseCitation(raw);

        expect(parsed).toEqual({
            type: 'L',
            entityId: 42,
            page: '5',
            start: '10',
            end: '20',
            keyword: 'Key',
            summary: 'Summary:with:colon',
        });
    });

    it('returns undefined for invalid citations', () => {
        expect(parseCitation('[cite:X:1]')).toBeUndefined();
        expect(parseCitation('[cite:L:not-number]')).toBeUndefined();
        expect(parseCitation('[cite:]')).toBeUndefined();
    });

    it('validates citation types', () => {
        expect(isCitationType('L')).toBe(true);
        expect(isCitationType('F')).toBe(true);
        expect(isCitationType('X')).toBe(false);
        expect(isCitationType(undefined)).toBe(false);
    });

    it('resolves citation type classes', () => {
        expect(resolveCitationTypeClass({ type: 'F', entityId: 1, page: '', start: '', end: '', keyword: '', summary: '' })).toBe('iris-citation--faq');
        expect(resolveCitationTypeClass({ type: 'L', entityId: 1, page: '', start: '00:01', end: '', keyword: '', summary: '' })).toBe('iris-citation--video');
        expect(resolveCitationTypeClass({ type: 'L', entityId: 1, page: '3', start: '', end: '', keyword: '', summary: '' })).toBe('iris-citation--slide');
        expect(resolveCitationTypeClass({ type: 'L', entityId: 1, page: '', start: '', end: '', keyword: '', summary: '' })).toBe('iris-citation--source');
    });

    it('formats citation labels with escaping and fallbacks', () => {
        const withKeyword = formatCitationLabel({ type: 'L', entityId: 1, page: '', start: '', end: '', keyword: '<b>Key</b>', summary: '' });
        const withoutKeyword = formatCitationLabel({ type: 'L', entityId: 1, page: '', start: '', end: '', keyword: '', summary: '' });
        const faqFallback = formatCitationLabel({ type: 'F', entityId: 1, page: '', start: '', end: '', keyword: '', summary: '' });

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
        const text = 'Hello [cite:L:7::::Keyword:Summary] world';

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
        const text = '[cite:L:1::::One:Summary] [cite:L:2::::Two:Summary]';

        const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

        expect(result).toContain('<group />');
        expect(renderGroup).toHaveBeenCalledOnce();
        expect(renderSingle).not.toHaveBeenCalled();
    });

    it('falls back to raw citations when a group contains invalid entries', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1' }];
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');
        const text = 'A [cite:L:1::::One:Summary] [cite:X:bad] B';

        const result = replaceCitationBlocks(text, citationInfo, { renderSingle, renderGroup });

        expect(renderGroup).not.toHaveBeenCalled();
        expect(renderSingle).not.toHaveBeenCalled();
        expect(result).toContain('[cite:L:1');
        expect(result).toContain('[cite:X:bad]');
    });

    it('ignores texts without citation markers', () => {
        const renderSingle = vi.fn().mockReturnValue('<single />');
        const renderGroup = vi.fn().mockReturnValue('<group />');
        const text = 'No citations here';

        const result = replaceCitationBlocks(text, [], { renderSingle, renderGroup });

        expect(result).toBe(text);
        expect(text.match(CITATION_REGEX)).toBeNull();
    });
});
