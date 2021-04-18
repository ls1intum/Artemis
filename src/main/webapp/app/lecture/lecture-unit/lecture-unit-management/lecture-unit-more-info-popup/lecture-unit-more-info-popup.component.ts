import { Component, Input, ViewEncapsulation } from '@angular/core';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

@Component({
    selector: 'jhi-lecture-unit-more-info-popup',
    templateUrl: './lecture-unit-more-info-popup.component.html',
    styleUrls: ['./lecture-unit-more-info-popup.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class LectureUnitMoreInfoPopupComponent {
    @Input()
    lectureUnit: LectureUnit;

    readonly LectureUnitType = LectureUnitType;

    getAttachmentVersion() {
        if (this.lectureUnit.type !== LectureUnitType.ATTACHMENT) {
            return undefined;
        } else {
            const attachmentUnit = this.lectureUnit as AttachmentUnit;
            if (attachmentUnit.attachment?.version) {
                return attachmentUnit.attachment.version.toString();
            } else {
                return undefined;
            }
        }
    }
}
