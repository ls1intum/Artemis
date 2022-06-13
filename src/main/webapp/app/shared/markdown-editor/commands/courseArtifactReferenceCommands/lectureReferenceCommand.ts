import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { MetisService } from 'app/shared/metis/metis.service';
import { ReferenceType } from 'app/shared/metis/metis.util';

export class LectureReferenceCommand extends MultiOptionCommand {
    metisService: MetisService;

    buttonTranslationString = 'artemisApp.metis.editor.lecture';

    constructor(metisService: MetisService) {
        super();
        this.metisService = metisService;

        this.setValues(
            this.metisService.getCourse().lectures!.map((lecture) => ({
                id: lecture.id!.toString(),
                value: lecture.title!,
                type: ReferenceType.LECTURE,
                elements: lecture.attachments?.map((attachment) => ({ id: attachment.id!.toString(), value: attachment.name!, courseArtifactType: ReferenceType.ATTACHMENT })),
            })),
        );
    }

    /**
     * @function execute
     * @param {string} selectedLectureId   ID of the lecture to be referenced
     * @desc                                Add a lecture reference link in markdown language
     *                                      1. Add '[{lecture-title}](/courses/{courseId}/lectures/{lectureId}})' at the cursor in the editor
     *                                      2. Link in markdown language appears which when clicked navigates to the lecture page
     */
    execute(selectedLectureId: string, type?: ReferenceType, selectedElementId?: string): void {
        const selectedLecture = this.metisService.getCourse().lectures!.find((value) => value.id!.toString() === selectedLectureId)!;
        const selectedAttachment = selectedLecture.attachments?.find((value) => value.id!.toString() === selectedElementId)!;
        const referenceLink =
            ReferenceType.LECTURE === type
                ? `[lecture](${this.metisService.getLinkForLecture(selectedLecture.id!.toString())})${selectedLecture.title}[/lecture]`
                : `[attachment](${selectedAttachment.link})${selectedAttachment.name}[/attachment]`;
        this.insertText(referenceLink);
        this.focus();
    }
}
