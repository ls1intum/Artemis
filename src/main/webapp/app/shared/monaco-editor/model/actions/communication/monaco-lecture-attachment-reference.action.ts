import { TranslateService } from '@ngx-translate/core';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { MetisService } from 'app/shared/metis/metis.service';
import { firstValueFrom } from 'rxjs';
import { LectureService } from 'app/lecture/lecture.service';
import { ReferenceType } from 'app/shared/metis/metis.util';
import { Lecture } from 'app/entities/lecture.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Attachment } from 'app/entities/attachment.model';
import { Slide } from 'app/entities/lecture-unit/slide.model';

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

    run(editor: monaco.editor.ICodeEditor, args?: { reference: ReferenceType; lecture: Lecture; attachmentUnit?: AttachmentUnit; slide?: Slide; attachment?: Attachment }): void {
        switch (args?.reference) {
            case ReferenceType.LECTURE:
                this.insertLectureOrAttachmentReference(editor, args.lecture, args.lecture.id!, ReferenceType.LECTURE);
                break;
            case ReferenceType.ATTACHMENT:
                this.insertLectureOrAttachmentReference(editor, args.lecture, args.attachment!.id!, ReferenceType.ATTACHMENT);
                break;
            case ReferenceType.SLIDE:
            case ReferenceType.ATTACHMENT_UNITS:
                this.insertAttachmentUnitReference(editor, args.lecture, args.attachmentUnit!, args.slide);
                break;
            default:
                throw new Error('Unsupported reference type.');
        }
    }

    insertAttachmentUnitReference(editor: monaco.editor.ICodeEditor, lectureWithDetails: Lecture, attachmentUnit: AttachmentUnit, slide?: Slide): void {
        if (slide) {
            const shortLink = slide.slideImagePath!.split('attachments/')[1];
            // Use a regular expression and the replace() method to remove the file name and last slash
            const shortLinkWithoutFileName: string = shortLink.replace(new RegExp(`[^/]*${'.png'}`), '').replace(/\/$/, '');
            const referenceLink = `[slide]${attachmentUnit.name} Slide ${slide.slideNumber}(${shortLinkWithoutFileName})[/slide]`;
            this.replaceTextAtCurrentSelection(editor, referenceLink);
            editor.focus();
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
