import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisCitationTextComponent } from './iris-citation-text.component';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { IrisCitationMaterialVersionService } from './iris-citation-material-version.service';
import { escapeHtml, formatCitationLabel, parseCitation, removeCitationBlocks, replaceCitationBlocks, resolveCitationTypeClass } from './iris-citation-text.util';

describe('IrisCitationTextComponent', () => {
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
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'L', lectureUnitTitle: '', lectureId: 1, courseId: 1 }];
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
            providers: [provideHttpClient(), provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(IrisCitationTextComponent);
    });

    it('renders text without citations', () => {
        const el = render('Hello world');
        expect(el.textContent).toContain('Hello world');
    });

    it('renders single citation with summary and lecture info', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'My Lecture', lectureUnitTitle: 'My Unit', lectureId: 1, courseId: 1 }];
        const el = render('Hello [cite:L:7::::Keyword:Summary] world', citationInfo);

        expect(el.querySelector('.iris-citation')).toBeTruthy();
        expect(el.querySelector('.iris-citation__summary-text')?.textContent?.trim()).toBe('Summary');
        expect(el.querySelector('.iris-citation__summary-row--unit .iris-citation__summary-value')?.textContent?.trim()).toBe('My Unit');
        expect(el.querySelector('.iris-citation__summary-row--lecture .iris-citation__summary-value')?.textContent?.trim()).toBe('My Lecture');
    });

    it('hides unit row when lectureUnitTitle is empty but still shows lecture', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: 'Lecture', lectureUnitTitle: '', lectureId: 1, courseId: 1 }];
        const el = render('[cite:L:7::::MyKeyword:Summary]', citationInfo);

        expect(el.querySelector('.iris-citation__summary-row--unit')).toBeFalsy();
        expect(el.querySelector('.iris-citation__summary-row--lecture .iris-citation__summary-value')?.textContent?.trim()).toBe('Lecture');
    });

    it('hides lecture metadata when no unit or lecture title is available', () => {
        const citationInfo: IrisCitationMetaDTO[] = [{ entityId: 7, lectureTitle: '', lectureUnitTitle: '', lectureId: 1, courseId: 1 }];
        const el = render('[cite:L:7:::::Summary]', citationInfo);

        expect(el.querySelector('.iris-citation__summary-divider')).toBeFalsy();
        expect(el.querySelector('.iris-citation__summary-meta')).toBeFalsy();
    });

    it('renders group without summary section', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'L1', lectureUnitTitle: '', lectureId: 1, courseId: 1 },
            { entityId: 2, lectureTitle: 'L2', lectureUnitTitle: '', lectureId: 1, courseId: 1 },
        ];
        const el = render('[cite:L:1:::::] [cite:L:2:::::]', citationInfo);

        expect(el.querySelector('.iris-citation-group')).toBeTruthy();
        expect(el.querySelector('.iris-citation-group--has-summary')).toBeFalsy();
        expect(el.querySelector('.iris-citation__summary')).toBeFalsy();
    });

    it('does not render navigation controls when group has only one summary', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'L1', lectureUnitTitle: '', lectureId: 1, courseId: 1 },
            { entityId: 2, lectureTitle: 'L2', lectureUnitTitle: '', lectureId: 1, courseId: 1 },
        ];
        const el = render('[cite:L:1::::One:Summary] [cite:L:2:::::]', citationInfo);

        expect(el.querySelector('.iris-citation-group--has-summary')).toBeTruthy();
        expect(el.querySelector('.iris-citation__summary-item')).toBeTruthy();
        expect(el.querySelector('.iris-citation__nav')).toBeFalsy();
    });

    it('keeps citation bubble text unchanged when navigating grouped summaries', () => {
        const citationInfo: IrisCitationMetaDTO[] = [
            { entityId: 1, lectureTitle: 'L1', lectureUnitTitle: '', lectureId: 1, courseId: 1 },
            { entityId: 2, lectureTitle: 'L2', lectureUnitTitle: '', lectureId: 1, courseId: 1 },
        ];
        const el = render('[cite:L:1:5:::FirstKeyword:S1] [cite:F:2::::SecondKeyword:S2]', citationInfo);

        const group = el.querySelector('.iris-citation-group--has-summary') as HTMLElement;
        const bubbleText = group.querySelector('.iris-citation__text') as HTMLElement;
        const navButtons = group.querySelectorAll('.iris-citation__nav-button') as NodeListOf<HTMLElement>;

        const initialText = bubbleText.textContent?.trim();

        navButtons[1].click();
        expect(bubbleText.textContent?.trim()).toBe(initialText);

        navButtons[0].click();
        expect(bubbleText.textContent?.trim()).toBe(initialText);
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

    describe('Citation click', () => {
        const meta = (): IrisCitationMetaDTO => ({
            entityId: 42,
            lectureTitle: 'Lecture',
            lectureUnitTitle: 'Unit',
            lectureId: 5,
            courseId: 9,
        });

        let navigate: ReturnType<typeof vi.spyOn>;
        let warning: ReturnType<typeof vi.spyOn>;
        let error: ReturnType<typeof vi.spyOn>;
        let getMaterialVersions: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
            warning = vi.spyOn(TestBed.inject(AlertService), 'warning').mockImplementation(() => undefined as any);
            error = vi.spyOn(TestBed.inject(AlertService), 'error').mockImplementation(() => undefined as any);
            getMaterialVersions = vi.spyOn(TestBed.inject(IrisCitationMaterialVersionService), 'getMaterialVersions');
        });

        /** Every citation navigation carries this, so the lecture page knows a specific unit was asked for and can say so when it cannot find it. */
        const unitOnly = { unit: '42' };

        const clickCitation = (text: string, citationInfo: IrisCitationMetaDTO[]) => {
            const el = render(text, citationInfo);
            const citation = el.querySelector('.iris-citation') as HTMLElement;
            expect(citation).toBeTruthy();
            citation.dispatchEvent(new MouseEvent('click', { bubbles: true }));
            return citation;
        };

        it('jumps to the exact page when the slides still have the pinned version', () => {
            getMaterialVersions.mockReturnValue(of({ attachmentVersion: 3 }));

            clickCitation('[cite:L:42:7:::Key:Summary:va3]', [meta()]);

            expect(getMaterialVersions).toHaveBeenCalledWith(42);
            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: { ...unitOnly, page: '7' } });
            expect(warning).not.toHaveBeenCalled();
        });

        it('opens the unit only and warns when the slides changed', () => {
            getMaterialVersions.mockReturnValue(of({ attachmentVersion: 4 }));

            clickCitation('[cite:L:42:7:::Key:Summary:va3]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: unitOnly });
            expect(warning).toHaveBeenCalledWith('artemisApp.iris.citation.outdated.stale');
        });

        it('jumps to the timestamp when the transcription still has the pinned version', () => {
            getMaterialVersions.mockReturnValue(of({ videoVersion: 2, hasVideo: true }));

            clickCitation('[cite:L:42::120:180:Key:Summary:vt2]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: { ...unitOnly, timestamp: '120' } });
            expect(warning).not.toHaveBeenCalled();
        });

        // A transcript segment carries the slide it was spoken over, but only the transcription was checked: the PDF may have changed on its own, so that page would be
        // an unverified slide presented as the cited one.
        it('leaves out the companion slide of a video citation', () => {
            getMaterialVersions.mockReturnValue(of({ videoVersion: 2, hasVideo: true, attachmentVersion: 5 }));

            clickCitation('[cite:L:42:7:120:180:Key:Summary:vt2]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: { ...unitOnly, timestamp: '120' } });
            expect(warning).not.toHaveBeenCalled();
        });

        it('compares a video citation against the transcription, not against the slides', () => {
            // The slides happen to sit at exactly the pinned number, which must not make the citation look unchanged
            getMaterialVersions.mockReturnValue(of({ attachmentVersion: 2 }));

            clickCitation('[cite:L:42:7:120:180:Key:Summary:vt2]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: unitOnly });
            expect(error).toHaveBeenCalledWith('artemisApp.iris.citation.outdated.gone');
        });

        // A citation with only an end time is linked to the page, so it must also be compared against the slides. The marker says so,
        // which is what keeps this in step with the server rather than depending on both sides reading the timestamps alike.
        it('compares a citation carrying only an end time against the slides', () => {
            getMaterialVersions.mockReturnValue(of({ attachmentVersion: 3, videoVersion: 9 }));

            clickCitation('[cite:L:42:7::180:Key:Summary:va3]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: { ...unitOnly, page: '7' } });
            expect(warning).not.toHaveBeenCalled();
        });

        it('opens the unit only and reports an error when the material is gone', () => {
            getMaterialVersions.mockReturnValue(of({}));

            clickCitation('[cite:L:42:7:::Key:Summary:va3]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: unitOnly });
            expect(error).toHaveBeenCalledWith('artemisApp.iris.citation.outdated.gone');
        });

        // While a video is being re-transcribed its transcription row is gone but the video plays as always, so calling it gone would be plainly false to anyone looking
        // at the page. There is simply nothing left to compare the cited timestamp against.
        it('reports an unverifiable citation rather than a gone one when the video outlived its transcription', () => {
            getMaterialVersions.mockReturnValue(of({ hasVideo: true }));

            clickCitation('[cite:L:42::120:180:Key:Summary:vt2]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: unitOnly });
            expect(warning).toHaveBeenCalledWith('artemisApp.iris.citation.outdated.unverified');
            expect(error).not.toHaveBeenCalled();
        });

        it('does not ask the server for a citation written before versions existed', () => {
            clickCitation('[cite:L:42:7:::Key:Summary]', [meta()]);

            expect(getMaterialVersions).not.toHaveBeenCalled();
            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: { ...unitOnly, page: '7' } });
            expect(warning).not.toHaveBeenCalled();
        });

        it('withholds the exact position when the check could not be made', () => {
            getMaterialVersions.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            clickCitation('[cite:L:42:7:::Key:Summary:va3]', [meta()]);

            // An unverified page number may well be the wrong one, so the link is kept while the jump is not
            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: unitOnly });
            expect(warning).toHaveBeenCalledWith('artemisApp.iris.citation.outdated.unverified');
        });

        // An unreachable unit is an answer, not a failed check: the lecture page words it better once it knows which units it has, so the click stays quiet.
        it.each([403, 404])('says nothing itself when the unit is unreachable (%i)', (status) => {
            getMaterialVersions.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));

            clickCitation('[cite:L:42:7:::Key:Summary:va3]', [meta()]);

            expect(navigate).toHaveBeenCalledWith(['/courses', '9', 'lectures', '5'], { queryParams: unitOnly });
            expect(warning).not.toHaveBeenCalled();
            expect(error).not.toHaveBeenCalled();
        });

        it('does not navigate when the lecture unit is gone', () => {
            clickCitation('[cite:L:42:7:::Key:Summary:va3]', []);

            expect(navigate).not.toHaveBeenCalled();
        });

        it('does not navigate for FAQ citations', () => {
            clickCitation('[cite:F:9::::Key:Summary]', []);

            expect(navigate).not.toHaveBeenCalled();
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
            { entityId: 1, lectureTitle: 'Lecture 1', lectureUnitTitle: 'Unit 1', lectureId: 1, courseId: 1 },
            { entityId: 7, lectureTitle: 'Lecture 7', lectureUnitTitle: 'Unit 7', lectureId: 1, courseId: 1 },
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

    describe('Citation revisions', () => {
        it('parses the pinned attachment version of a slide citation', () => {
            expect(parseCitation('[cite:L:42:7:::Key:Summary:va3]')).toEqual({
                type: 'L',
                entityId: '42',
                page: '7',
                start: '',
                end: '',
                keyword: 'Key',
                summary: 'Summary',
                pinnedVersion: { kind: 'attachment', version: '3' },
            });
        });

        it('parses the pinned transcription version of a video citation', () => {
            expect(parseCitation('[cite:L:42:7:120:180:Key:Summary:vt2]')?.pinnedVersion).toEqual({ kind: 'video', version: '2' });
        });

        it('keeps a summary containing colons intact in front of the version field', () => {
            const parsed = parseCitation('[cite:L:42:7:::Key:Summary:with:colon:va3]');

            expect(parsed?.keyword).toBe('Key');
            expect(parsed?.summary).toBe('Summary:with:colon');
            expect(parsed?.pinnedVersion).toEqual({ kind: 'attachment', version: '3' });
        });

        it('treats a trailing untagged field as part of the summary', () => {
            const parsed = parseCitation('[cite:L:42:7:::Key:Summary:with:colon]');

            expect(parsed?.pinnedVersion).toBeUndefined();
            expect(parsed?.summary).toBe('Summary:with:colon');
        });

        it('does not read empty trailing fields as a version field', () => {
            const parsed = parseCitation('[cite:L:42:7:::Key:Summary:with:colon::]');

            expect(parsed?.pinnedVersion).toBeUndefined();
            expect(parsed?.summary).toBe('Summary:with:colon::');
        });

        // The tag is what a summary cannot accidentally produce. A trailing number alone used to be read as a version, which let a
        // citation of changed slides pass as current whenever the unrelated number happened to match.
        it.each(['[cite:L:42:7:::Key:Ratios:3:1]', '[cite:L:42:7:::Key:Chapter 3:1]'])('leaves a colon-numeric summary entirely in the summary (%s)', (raw) => {
            const parsed = parseCitation(raw);

            expect(parsed?.pinnedVersion).toBeUndefined();
            expect(parsed?.summary).toBe(raw.slice('[cite:L:42:7:::Key:'.length, -1));
        });

        // A stamped citation whose summary happens to contain colons is still read correctly: only the tagged last field is taken off.
        it('reads the version off a stamped citation whose summary contains colons and numbers', () => {
            const parsed = parseCitation('[cite:L:42:7:::Key:Ratios:3:vt1]');

            expect(parsed?.pinnedVersion).toEqual({ kind: 'video', version: '1' });
            expect(parsed?.summary).toBe('Ratios:3');
        });
    });
});
