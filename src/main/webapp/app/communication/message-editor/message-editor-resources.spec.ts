import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { FileService } from 'app/foundation/service/file.service';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqState } from 'app/communication/shared/entities/faq.model';
import { MessageEditorResources } from './message-editor-resources';

describe('Formatted message resources', () => {
    const lectures = { findAllByCourseIdWithSlides: vi.fn() };
    const faqs = { findAllByCourseIdAndState: vi.fn() };
    const files = { createStudentLink: vi.fn(), addAttachmentVersionToUrl: vi.fn() };
    let resources: MessageEditorResources;

    beforeEach(() => {
        vi.clearAllMocks();
        lectures.findAllByCourseIdWithSlides.mockReturnValue(
            of(
                new HttpResponse({
                    body: [
                        {
                            id: 2,
                            title: 'Lecture 2',
                            lectureUnits: [
                                {
                                    type: 'attachment',
                                    name: 'Slides',
                                    attachment: { link: '/attachments/lecture/handout.pdf', studentVersion: '/attachments/lecture/student.pdf', version: 3 },
                                    slides: [
                                        { id: 7, slideNumber: 3 },
                                        { id: 8, slideNumber: 5 },
                                    ],
                                },
                                { type: 'text', name: 'Text unit' },
                            ],
                        },
                    ],
                }),
            ),
        );
        faqs.findAllByCourseIdAndState.mockReturnValue(of(new HttpResponse({ body: [{ id: 9, questionTitle: 'How to submit?' }] })));
        files.addAttachmentVersionToUrl.mockReturnValue('/attachments/lecture/student.pdf?version=3');
        files.createStudentLink.mockReturnValue('/attachments/lecture/student.pdf');
        TestBed.configureTestingModule({
            providers: [
                MessageEditorResources,
                { provide: LectureService, useValue: lectures },
                { provide: FaqService, useValue: faqs },
                { provide: FileService, useValue: files },
            ],
        });
        resources = TestBed.inject(MessageEditorResources);
    });

    it('loads only published FAQs and retains their existing reference format', async () => {
        expect(await resources.search('faq', 'submit', 1)).toEqual([{ label: 'How to submit?', kind: 'faq', markdown: '[faq]How to submit?(/courses/1/faq?faqId=9)[/faq]' }]);
        expect(faqs.findAllByCourseIdAndState).toHaveBeenCalledWith(1, FaqState.ACCEPTED);
        await resources.search('faq', '', 1);
        expect(faqs.findAllByCourseIdAndState).toHaveBeenCalledTimes(1);
    });

    it('offers attachment units and individual slides with versioned student links', async () => {
        const result = await resources.search('slides', '', 1);
        expect(result.map((item) => item.markdown)).toEqual([
            '[lecture]Lecture 2(/courses/1/lectures/2)[/lecture]',
            '[lecture-unit]Slides(lecture/student.pdf?version=3)[/lecture-unit]',
            '[slide]Slides Slide 3(#7)[/slide]',
            '[slide]Slides Slide 5(#8)[/slide]',
        ]);
        expect(result[2].label).toContain('Lecture 2');
        expect(result[2]).toMatchObject({ slideNumber: 3, resourceLabel: 'Lecture 2 › Slides', previewUrl: '/api/core/files/slides/7' });
        expect(files.addAttachmentVersionToUrl).toHaveBeenCalledWith('/attachments/lecture/student.pdf', 3);
        expect(await resources.search('slides', '2', 1)).toHaveLength(4);
        expect(lectures.findAllByCourseIdWithSlides).toHaveBeenCalledTimes(1);
    });

    it.each(['/api/core/files/slides/7', '/attachments/', ''])('skips attachment units with an unusable versioned link: %s', async (link) => {
        files.addAttachmentVersionToUrl.mockReturnValue(link);

        expect((await resources.search('slides', '', 1)).map((item) => item.kind)).toEqual(['lecture']);
    });

    it('finds lectures that have no attachment units or slides', async () => {
        lectures.findAllByCourseIdWithSlides.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ id: 4, title: 'Intro [recap]' }, { id: 5, title: 'Unit-free lecture', lectureUnits: [{ type: 'text', name: 'Text unit' }] }, { id: 6 }],
                }),
            ),
        );

        expect(await resources.search('slides', 'lecture', 1)).toEqual([
            { label: 'Unit-free lecture', kind: 'lecture', markdown: '[lecture]Unit-free lecture(/courses/1/lectures/5)[/lecture]' },
        ]);
        expect((await resources.search('slides', 'intro', 1)).map((item) => item.label)).toEqual(['Intro [recap]']);
    });

    it('separates course caches and retries failed loads', async () => {
        faqs.findAllByCourseIdAndState.mockReturnValueOnce(throwError(() => new Error('offline')));
        await expect(resources.search('faq', '', 1)).rejects.toThrow('offline');
        await resources.search('faq', '', 1);
        await resources.search('faq', '', 2);
        expect(faqs.findAllByCourseIdAndState).toHaveBeenCalledTimes(3);
    });
});
