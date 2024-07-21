import { TranslateService } from '@ngx-translate/core';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { MetisService } from 'app/shared/metis/metis.service';
import { firstValueFrom } from 'rxjs';
import { LectureService } from 'app/lecture/lecture.service';
import { ReferenceType } from 'app/shared/metis/metis.util';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

export class MonacoLectureAttachmentReferenceAction extends MonacoEditorAction {
    static readonly ID = 'monaco-lecture-attachment-reference.action';

    lecturesWithDetails: Lecture[] = [];

    constructor(
        private readonly metisService: MetisService,
        private readonly lectureService: LectureService,
    ) {
        super(MonacoLectureAttachmentReferenceAction.ID, 'artemisApp.metis.editor.lecture');
        firstValueFrom(this.lectureService.findAllByCourseIdWithSlides(this.metisService.getCourse().id!)).then((response) => {
            const lectures = response.body;
            if (lectures) {
                this.lecturesWithDetails = lectures;
            }
        });
    }

    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
    }

    run(editor: monaco.editor.ICodeEditor, args?: { lectureId: number; type?: ReferenceType; attachmentId?: number; attachmentUnitId?: number; slideId?: number }): void {
        const lecture = args?.lectureId ? this.metisService.getCourse().lectures?.find((value) => value.id === args.lectureId) : undefined;
        if (lecture) {
            return;
        }
        // Safe because lecture would be undefined if args were undefined
        const lectureId = args!.lectureId;
        firstValueFrom(this.lectureService.findWithDetailsWithSlides(lectureId)).then((response) => {
            const lectureWithDetails = response.body!;
            if (args?.attachmentUnitId) {
                const selectedAttachmentUnit: AttachmentUnit = <AttachmentUnit>(
                    lectureWithDetails.lectureUnits?.filter((f) => f.type === LectureUnitType.ATTACHMENT).find((value) => value.id === args.attachmentUnitId)
                );
                if (selectedAttachmentUnit) {
                    this.insertAttachmentUnitReference(editor, lectureWithDetails, selectedAttachmentUnit, args.slideId);
                }
            } else if (args?.attachmentId && args?.type) {
                this.insertLectureOrAttachmentReference(editor, lectureWithDetails, args.attachmentId, args.type);
            }
        });
    }

    insertAttachmentUnitReference(editor: monaco.editor.ICodeEditor, lectureWithDetails: Lecture, attachmentUnit: AttachmentUnit, slideId?: number): void {
        if (slideId) {
            const slide = attachmentUnit.slides?.find((value) => value.id === slideId);
            if (slide) {
                const shortLink = slide.slideImagePath!.split('attachments/')[1];
                // Use a regular expression and the replace() method to remove the file name and last slash
                const shortLinkWithoutFileName: string = shortLink.replace(new RegExp(`[^/]*${'.png'}`), '').replace(/\/$/, '');
                const referenceLink = `[slide]${attachmentUnit.name} Slide ${slide.slideNumber}(${shortLinkWithoutFileName})[/slide]`;
                this.replaceTextAtCurrentSelection(editor, referenceLink);
                editor.focus();
            }
        } else {
            const shortLink = attachmentUnit.attachment?.link!.split('attachments/')[1];
            const referenceLink = `[lecture-unit]${attachmentUnit.name}(${shortLink})[/lecture-unit]`;
            this.replaceTextAtCurrentSelection(editor, referenceLink);
            editor.focus();
        }
    }

    insertLectureOrAttachmentReference(editor: monaco.editor.ICodeEditor, lectureWithDetails: Lecture, attachmentId: number, referenceType: ReferenceType) {
        const selectedAttachment = lectureWithDetails.attachments?.find((value) => value.id === attachmentId);
        const shortLink = selectedAttachment?.link ? selectedAttachment.link.split('attachments/')[1] : undefined;
        const referenceLink =
            ReferenceType.LECTURE === referenceType
                ? `[lecture]${lectureWithDetails.title}(${this.metisService.getLinkForLecture(lectureWithDetails.id!.toString())})[/lecture]`
                : `[attachment]${selectedAttachment!.name}(${shortLink})[/attachment]`;
        this.replaceTextAtCurrentSelection(editor, referenceLink);
        editor.focus();
    }
}
