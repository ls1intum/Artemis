import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { FileService } from 'app/foundation/service/file.service';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqState } from 'app/communication/shared/entities/faq.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';
import { MessageSuggestion } from './message-editor-suggestions';

export type MessageResourceKind = 'slides' | 'faq';

/** Lazily loads the existing course resource endpoints, with a cache owned by this composer. */
@Injectable()
export class MessageEditorResources {
    private readonly lectures = inject(LectureService);
    private readonly files = inject(FileService);
    private readonly faqs = inject(FaqService);
    private readonly cache = new Map<string, Promise<MessageSuggestion[]>>();

    async search(kind: MessageResourceKind, query: string, courseId: number): Promise<MessageSuggestion[]> {
        const key = `${courseId}:${kind}`;
        let items = this.cache.get(key);
        if (!items) {
            items = (kind === 'faq' ? this.loadFaqs(courseId) : this.loadSlides(courseId)).catch((error: unknown) => {
                this.cache.delete(key);
                throw error;
            });
            this.cache.set(key, items);
        }
        return (await items).filter((item) => item.label.toLowerCase().includes(query.toLowerCase())).slice(0, 50);
    }

    private async loadFaqs(courseId: number): Promise<MessageSuggestion[]> {
        const response = await firstValueFrom(this.faqs.findAllByCourseIdAndState(courseId, FaqState.ACCEPTED));
        return (response.body ?? [])
            .filter((faq) => faq.id && faq.questionTitle)
            .map((faq) => ({
                label: faq.questionTitle!,
                kind: 'faq',
                markdown: `[faq]${sanitizeStringForMarkdownEditor(faq.questionTitle)}(/courses/${courseId}/faq?faqId=${faq.id})[/faq]`,
            }));
    }

    private async loadSlides(courseId: number): Promise<MessageSuggestion[]> {
        const response = await firstValueFrom(this.lectures.findAllByCourseIdWithSlides(courseId));
        return (response.body ?? []).flatMap((lecture) =>
            (lecture.lectureUnits ?? [])
                .filter((unit): unit is AttachmentVideoUnit => unit.type === LectureUnitType.ATTACHMENT_VIDEO)
                .flatMap((unit): MessageSuggestion[] => {
                    if (!unit.attachment?.link) {
                        return [];
                    }
                    const attachment = unit.attachment;
                    const studentLink = attachment.studentVersion || this.files.createStudentLink(attachment.link!);
                    const link = this.files.addAttachmentVersionToUrl(studentLink, attachment.version);
                    const shortLink = link.split('attachments/')[1];
                    const label = `${lecture.title} › ${unit.name}`;
                    return [
                        { label, kind: 'lecture-unit', markdown: `[lecture-unit]${sanitizeStringForMarkdownEditor(unit.name)}(${shortLink})[/lecture-unit]` },
                        ...(unit.slides ?? [])
                            .filter((slide) => !!slide.id)
                            .map((slide, index): MessageSuggestion => ({
                                label: `${label} › ${slide.slideNumber ?? index + 1}`,
                                resourceLabel: label,
                                slideNumber: slide.slideNumber ?? index + 1,
                                previewUrl: `/api/core/files/slides/${slide.id}`,
                                kind: 'slide',
                                markdown: `[slide]${sanitizeStringForMarkdownEditor(unit.name)} Slide ${slide.slideNumber ?? index + 1}(#${slide.id})[/slide]`,
                            })),
                    ];
                }),
        );
    }
}
