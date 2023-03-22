import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { MetisService } from 'app/shared/metis/metis.service';
import { ReferenceType } from 'app/shared/metis/metis.util';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { debounceTime, filter, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

export class LectureAttachmentUnitReferenceCommand extends MultiOptionCommand {
    metisService: MetisService;
    lectureService: LectureService;
    lectures: Lecture[] = [];

    buttonTranslationString = 'artemisApp.metis.editor.attachmentUnit';

    constructor(metisService: MetisService, lectureService: LectureService) {
        super();
        this.metisService = metisService;
        this.lectureService = lectureService;

        // we could also simply load all units for the lecture (as the lecture is already available through the route, see TODO above)
        this.metisService.getCourse().lectures?.map((lecture) => {
            console.log(lecture);
            this.lectureService
                .findWithDetails(lecture.id!)
                .pipe(map((response: HttpResponse<Lecture>) => response.body!))
                .subscribe({
                    next: (lecture: any) => {
                        this.lectures.push(lecture);
                        // this.setValues(
                        //     // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                        //     this.lectures?.map((lecture) => ({
                        //         id: lecture.id!.toString(),
                        //         value: lecture.title!,
                        //         type: ReferenceType.LECTURE,
                        //         attachmentUnits: lecture.lectureUnits?.map((unit) => ({
                        //             id: unit.id!.toString(),
                        //             value: unit.name!,
                        //             courseArtifactType: ReferenceType.ATTACHMENT_UNITS,
                        //         })),
                        //     }))!,
                        // );
                        this.setValues(
                            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                            [
                                ...this.values,
                                {
                                    id: lecture.id!.toString(),
                                    value: lecture.title!,
                                    type: ReferenceType.LECTURE,
                                    attachmentUnits: lecture.lectureUnits?.map((unit: any) => ({
                                        id: unit.id!.toString(),
                                        value: unit.name!,
                                        courseArtifactType: ReferenceType.ATTACHMENT_UNITS,
                                    })),
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
     * @param selectedUnitElementId
     * @desc                                Add a lecture reference link in markdown language
     *                                      1. Add '[{lecture-title}](/courses/{courseId}/lectures/{lectureId}})' at the cursor in the editor
     *                                      2. Link in markdown language appears which when clicked navigates to the lecture page
     */
    execute(selectedLectureId: string, type?: ReferenceType, selectedElementId?: string, selectedUnitElementId?: string): void {
        const selectedLecture = this.metisService.getCourse().lectures!.find((value) => value.id!.toString() === selectedLectureId)!;
        this.lectureService
            .findWithDetails(selectedLecture.id!)
            .pipe(map((response: HttpResponse<Lecture>) => response.body!))
            .subscribe({
                next: (lecture: Lecture) => {
                    console.log(lecture);
                    // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                    const selectedAttachment: AttachmentUnit = lecture.lectureUnits?.find((value: AttachmentUnit) => value.id!.toString() === selectedElementId)!;
                    // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain

                    const referenceLink = `[unitSlide]${selectedAttachment.name}(${selectedAttachment.attachment?.link})[/unitSlide]`;
                    this.insertText(referenceLink);
                    this.focus();
                },
            });
    }
}
