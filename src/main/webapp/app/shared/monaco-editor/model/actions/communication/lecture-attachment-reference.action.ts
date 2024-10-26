import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { firstValueFrom } from 'rxjs';
import { LectureService } from 'app/lecture/lecture.service';
import { ReferenceType } from 'app/shared/metis/metis.util';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Attachment } from 'app/entities/attachment.model';
import { Slide } from 'app/entities/lecture-unit/slide.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { sanitizeStringForMarkdownEditor } from 'app/shared/util/markdown.util';

interface LectureWithDetails {
    id: number;
    title: string;
    attachmentUnits?: AttachmentUnit[];
    attachments?: Attachment[];
}

interface LectureAttachmentReferenceActionArgs {
    reference: ReferenceType;
    lecture: LectureWithDetails;
    attachmentUnit?: AttachmentUnit;
    slide?: Slide;
    attachment?: Attachment;
}

/**
 * Action to insert a reference to a lecture, attachment, slide, or attachment unit into the editor.
 * The specific format of the reference depends on the type of reference.
 */
export class LectureAttachmentReferenceAction extends TextEditorAction {
    static readonly ID = 'lecture-attachment-reference.action';

    lecturesWithDetails: LectureWithDetails[] = [];

    constructor(
        private readonly metisService: MetisService,
        private readonly lectureService: LectureService,
    ) {
        super(LectureAttachmentReferenceAction.ID, 'artemisApp.metis.editor.lecture');
        firstValueFrom(this.lectureService.findAllByCourseIdWithSlides(this.metisService.getCourse().id!)).then((response) => {
            const lectures = response.body;
            if (lectures) {
                this.lecturesWithDetails = lectures
                    .filter((lecture) => !!lecture.id && !!lecture.title)
                    .map((lecture) => ({
                        id: lecture.id!,
                        title: lecture.title!,
                        attachmentUnits: lecture.lectureUnits?.filter((unit) => unit.type === LectureUnitType.ATTACHMENT),
                        attachments: lecture.attachments,
                    }));
            }
        });
    }

    /**
     * Executes the action in the current editor for the given arguments (lecture, attachment, slide, and/or attachment unit).
     * @param args The arguments to execute the action with.
     */
    executeInCurrentEditor(args: LectureAttachmentReferenceActionArgs): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Inserts, at the current position, a reference to the specified lecture, attachment, slide, or attachment unit.
     * Depending on the reference type, the reference will be formatted differently:
     * - Lecture: [lecture]Lecture Title(link)[/lecture]
     * - Attachment: [attachment]Attachment Name(link)[/attachment]
     * - Slide: [slide]Attachment Unit Name Slide Number(link)[/slide]
     * - Attachment Unit: [lecture-unit]Attachment Unit Name(link)[/lecture-unit]
     * @param editor The editor to insert the reference in.
     * @param args An object containing the item to reference and the type of reference.
     */
    run(editor: TextEditor, args?: LectureAttachmentReferenceActionArgs): void {
        switch (args?.reference) {
            case ReferenceType.LECTURE:
                this.insertLectureReference(editor, args.lecture);
                break;
            case ReferenceType.ATTACHMENT:
                if (args.attachment) {
                    this.insertAttachmentReference(editor, args.attachment);
                } else {
                    throw new Error(`[${this.id}] No attachment provided to reference.`);
                }
                break;
            case ReferenceType.ATTACHMENT_UNITS:
                if (args.attachmentUnit) {
                    this.insertAttachmentUnitReference(editor, args.attachmentUnit);
                } else {
                    throw new Error(`[${this.id}] No attachment unit provided to reference.`);
                }
                break;
            case ReferenceType.SLIDE:
                if (args.attachmentUnit && args.slide) {
                    this.insertSlideReference(editor, args.attachmentUnit, args.slide);
                } else {
                    throw new Error(`[${this.id}] No attachment unit or slide provided to reference.`);
                }
                break;
            default:
                throw new Error(`[${this.id}] Unsupported reference type.`);
        }
        editor.focus();
    }

    dispose() {
        super.dispose();
        this.lecturesWithDetails = [];
    }

    insertLectureReference(editor: TextEditor, lecture: LectureWithDetails): void {
        this.replaceTextAtCurrentSelection(
            editor,
            `[lecture]${sanitizeStringForMarkdownEditor(lecture.title)}(${this.metisService.getLinkForLecture(lecture.id.toString())})[/lecture]`,
        );
    }

    insertAttachmentReference(editor: TextEditor, attachment: Attachment): void {
        const shortLink = attachment.link?.split('attachments/')[1];
        this.replaceTextAtCurrentSelection(editor, `[attachment]${sanitizeStringForMarkdownEditor(attachment.name)}(${shortLink})[/attachment]`);
    }

    insertSlideReference(editor: TextEditor, attachmentUnit: AttachmentUnit, slide: Slide): void {
        const shortLink = slide.slideImagePath?.split('attachments/')[1];
        // Remove the trailing slash and the file name.
        const shortLinkWithoutFileName = shortLink?.replace(new RegExp(`[^/]*${'.png'}`), '').replace(/\/$/, '');
        this.replaceTextAtCurrentSelection(
            editor,
            `[slide]${sanitizeStringForMarkdownEditor(attachmentUnit.name)} Slide ${slide.slideNumber}(${shortLinkWithoutFileName})[/slide]`,
        );
    }

    insertAttachmentUnitReference(editor: TextEditor, attachmentUnit: AttachmentUnit): void {
        const shortLink = attachmentUnit.attachment?.link!.split('attachments/')[1];
        this.replaceTextAtCurrentSelection(editor, `[lecture-unit]${sanitizeStringForMarkdownEditor(attachmentUnit.name)}(${shortLink})[/lecture-unit]`);
    }
}
