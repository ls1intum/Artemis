import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { MetisService } from 'app/communication/service/metis.service';
import { firstValueFrom } from 'rxjs';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ReferenceType } from 'app/communication/metis.util';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { sanitizeStringForMarkdownEditor } from 'app/shared/util/markdown.util';
import { cloneDeep } from 'lodash-es';
import { addPublicFilePrefix } from 'app/app.constants';
import { FileService } from 'app/shared/service/file.service';

export interface LectureWithDetails {
    id: number;
    title: string;
    attachmentVideoUnits?: AttachmentVideoUnit[];
    attachments?: Attachment[];
}

interface LectureAttachmentReferenceActionArgs {
    reference: ReferenceType;
    lecture: LectureWithDetails;
    attachmentVideoUnit?: AttachmentVideoUnit;
    slide?: Slide;
    attachment?: Attachment;
    slideIndex?: number;
}

/**
 * Action to insert a reference to a lecture, attachment, slide, or attachment video unit into the editor.
 * The specific format of the reference depends on the type of reference.
 */
export class LectureAttachmentReferenceAction extends TextEditorAction {
    static readonly ID = 'lecture-attachment-reference.action';

    lecturesWithDetails: LectureWithDetails[] = [];

    constructor(
        private readonly metisService: MetisService,
        private readonly lectureService: LectureService,
        private readonly fileService: FileService,
    ) {
        super(LectureAttachmentReferenceAction.ID, 'artemisApp.metis.editor.lecture');
        firstValueFrom(this.lectureService.findAllByCourseIdWithSlides(this.metisService.getCourse().id!)).then((response) => {
            const lectures = response.body;
            if (lectures) {
                this.lecturesWithDetails = lectures
                    .filter((lecture) => !!lecture.id && !!lecture.title)
                    .map((lecture) => {
                        const attachmentsWithFileUrls = cloneDeep(lecture.attachments)?.map((attachment) => {
                            if (attachment.link && attachment.name) {
                                attachment.link = this.fileService.createAttachmentFileUrl(attachment.link!, attachment.name!, false);
                                attachment.linkUrl = addPublicFilePrefix(attachment.link);
                            }

                            return attachment;
                        });

                        return {
                            id: lecture.id!,
                            title: lecture.title!,
                            attachmentVideoUnits: lecture.lectureUnits?.filter((unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO),
                            attachments: attachmentsWithFileUrls,
                        };
                    });
            }
        });
    }

    /**
     * Executes the action in the current editor for the given arguments (lecture, attachment, slide, and/or attachment video unit).
     * @param args The arguments to execute the action with.
     */
    executeInCurrentEditor(args: LectureAttachmentReferenceActionArgs): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Inserts, at the current position, a reference to the specified lecture, attachment, slide, or attachment video unit.
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
                if (args.attachmentVideoUnit) {
                    this.insertAttachmentVideoUnitReference(editor, args.attachmentVideoUnit);
                } else {
                    throw new Error(`[${this.id}] No attachment video unit provided to reference.`);
                }
                break;
            case ReferenceType.SLIDE:
                if (args.attachmentVideoUnit && args.slide && args.slideIndex) {
                    this.insertSlideReference(editor, args.attachmentVideoUnit, args.slide, args.slideIndex);
                } else {
                    throw new Error(`[${this.id}] No attachment video unit or slide provided to reference.`);
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

    insertSlideReference(editor: TextEditor, attachmentVideoUnit: AttachmentVideoUnit, slide: Slide, slideIndex: number): void {
        // Using the new pattern that directly references the slide by ID with # prefix
        this.replaceTextAtCurrentSelection(editor, `[slide]${sanitizeStringForMarkdownEditor(attachmentVideoUnit.name)} Slide ${slideIndex}(#${slide.id})[/slide]`);
    }

    insertAttachmentVideoUnitReference(editor: TextEditor, attachmentVideoUnit: AttachmentVideoUnit): void {
        const attachment = attachmentVideoUnit.attachment;
        if (attachment && attachment.link) {
            const link = attachment.studentVersion || this.fileService.createStudentLink(attachment.link!);
            const shortLink = link.split('attachments/')[1];
            this.replaceTextAtCurrentSelection(editor, `[lecture-unit]${sanitizeStringForMarkdownEditor(attachmentVideoUnit.name)}(${shortLink})[/lecture-unit]`);
        }
    }
}
