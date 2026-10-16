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
                                    slides: [{ id: 7 }, { id: 8 }],
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
            '[lecture-unit]Slides(lecture/student.pdf?version=3)[/lecture-unit]',
            '[slide]Slides Slide 1(#7)[/slide]',
            '[slide]Slides Slide 2(#8)[/slide]',
        ]);
        expect(result[1].label).toContain('Lecture 2');
        expect(files.addAttachmentVersionToUrl).toHaveBeenCalledWith('/attachments/lecture/student.pdf', 3);
        expect(await resources.search('slides', '2', 1)).toHaveLength(3);
        expect(lectures.findAllByCourseIdWithSlides).toHaveBeenCalledTimes(1);
    });

    it('separates course caches and retries failed loads', async () => {
        faqs.findAllByCourseIdAndState.mockReturnValueOnce(throwError(() => new Error('offline')));
        await expect(resources.search('faq', '', 1)).rejects.toThrow('offline');
        await resources.search('faq', '', 1);
        await resources.search('faq', '', 2);
        expect(faqs.findAllByCourseIdAndState).toHaveBeenCalledTimes(3);
    });
});
