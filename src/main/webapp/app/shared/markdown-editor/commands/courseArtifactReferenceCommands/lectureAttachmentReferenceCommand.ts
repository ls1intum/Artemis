import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { MetisService } from 'app/shared/metis/metis.service';
import { ReferenceType } from 'app/shared/metis/metis.util';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { map } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Slide } from 'app/entities/lecture-unit/slide.model';

export class LectureAttachmentReferenceCommand extends MultiOptionCommand {
    metisService: MetisService;

    lectureService: LectureService;

    buttonTranslationString = 'artemisApp.metis.editor.lecture';

    constructor(metisService: MetisService, lectureService: LectureService) {
        super();
        this.metisService = metisService;
        this.lectureService = lectureService;

        this.metisService.getCourse().lectures?.map((lecture) => {
            this.lectureService
                .findWithDetails(lecture.id!)
                .pipe(map((response: HttpResponse<Lecture>) => response.body!))
                .subscribe({
                    next: (lecture: Lecture) => {
                        this.setValues(
                            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                            [
                                ...this.values,
                                {
                                    id: lecture.id!.toString(),
                                    value: lecture.title!,
                                    type: ReferenceType.LECTURE,
                                    elements: lecture.attachments?.map((attachment: any) => ({
                                        id: attachment.id!.toString(),
                                        value: attachment.name!,
                                        courseArtifactType: ReferenceType.ATTACHMENT,
                                    })),
                                    attachmentUnits: lecture.lectureUnits?.map((unit: any) => {
                                        return {
                                            id: unit.id!.toString(),
                                            value: unit.name!,
                                            slides: unit.slides
                                                ?.map((slide: Slide) => {
                                                    return {
                                                        id: slide.id!.toString(),
                                                        slideNumber: slide.slideNumber!,
                                                        slideImagePath: slide.slideImagePath!,
                                                        courseArtifactType: ReferenceType.SLIDE,
                                                    };
                                                })
                                                .sort((a: Slide, b: Slide) => a.slideNumber! - b.slideNumber!),
                                            courseArtifactType: ReferenceType.ATTACHMENT_UNITS,
                                        };
                                    }),
                                },
                            ],
                        );
                    },
                });
        });
    }

    /**
     * @function execute
     * @param {string} selectedLectureId   ID of the lecture to be referenced
     * @param type
     * @param selectedElementId
     * @param selectedUnitId
     * @param selectedSlideId
     * @desc                                Add a lecture reference link in markdown language
     *                                      1. Add '[{lecture-title}](/courses/{courseId}/lectures/{lectureId}})' at the cursor in the editor
     *                                      2. Link in markdown language appears which when clicked navigates to the lecture page
     */
    execute(selectedLectureId: string, type?: ReferenceType, selectedElementId?: string, selectedUnitId?: string, selectedSlideId?: string): void {
        const selectedLecture = this.metisService.getCourse().lectures!.find((value) => value.id!.toString() === selectedLectureId)!;
        this.lectureService
            .findWithDetails(selectedLecture.id!)
            .pipe(map((response: HttpResponse<Lecture>) => response.body!))
            .subscribe({
                next: (lecture: Lecture) => {
                    if (selectedUnitId) {
                        if (!selectedSlideId) {
                            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                            const selectedUnit: AttachmentUnit = lecture.lectureUnits?.find((value: AttachmentUnit) => value.id!.toString() === selectedUnitId)!;
                            const shortLink = selectedUnit.attachment?.link!.split('attachments/')[1];
                            const referenceLink = `[lecture-unit]${selectedUnit.name}(${shortLink})[/lecture-unit]`;
                            this.insertText(referenceLink);
                            this.focus();
                        } else {
                            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                            const selectedUnit: AttachmentUnit = lecture.lectureUnits?.find((value: AttachmentUnit) => value.id!.toString() === selectedUnitId)!;
                            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                            const selectedSlide: Slide = selectedUnit.slides?.find((value: Slide) => value.id!.toString() === selectedSlideId)!;
                            const shortLink = selectedSlide.slideImagePath!.split('attachments/')[1];
                            // Use a regular expression and the replace() method to remove the file name and last slash
                            const shortLinkWithoutFileName: string = shortLink.replace(new RegExp(`[^/]*${'.png'}`), '').replace(/\/$/, '');
                            const referenceLink = `[slide]${selectedUnit.name} Slide ${selectedSlide.slideNumber}(${shortLinkWithoutFileName})[/slide]`;
                            this.insertText(referenceLink);
                            this.focus();
                        }
                    } else {
                        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                        const selectedAttachment = selectedLecture.attachments?.find((value) => value.id!.toString() === selectedElementId)!;
                        const shortLink = selectedAttachment ? selectedAttachment.link!.split('attachments/')[1] : '';
                        const referenceLink =
                            ReferenceType.LECTURE === type
                                ? `[lecture]${selectedLecture.title}(${this.metisService.getLinkForLecture(selectedLecture.id!.toString())})[/lecture]`
                                : `[attachment]${selectedAttachment.name}(${shortLink})[/attachment]`;
                        this.insertText(referenceLink);
                        this.focus();
                    }
                },
            });
    }
}
