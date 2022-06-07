import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseArtifactType } from 'app/shared/markdown-editor/command-constants';

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
                type: CourseArtifactType.LECTURE,
                elements: lecture.attachments?.map((attachment) => ({ id: attachment.id!.toString(), value: attachment.name!, courseArtifactType: CourseArtifactType.ATTACHMENT })),
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
    execute(selectedLectureId: string, type?: CourseArtifactType): void {
        const selectedLecture = this.getValues().find((value) => value.id.toString() === selectedLectureId);
        const referenceLink = '[' + selectedLecture!.value + '](' + this.metisService.getLinkForLecture(selectedLecture!.id) + ')';
        this.insertText(referenceLink);
        this.focus();
    }
}
