import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faDownload } from '@fortawesome/free-solid-svg-icons/faDownload';
import { faLink } from '@fortawesome/free-solid-svg-icons/faLink';
import { faQuestion } from '@fortawesome/free-solid-svg-icons/faQuestion';
import { faScroll } from '@fortawesome/free-solid-svg-icons/faScroll';

// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in LectureUnit.java
export enum LectureUnitType {
    ATTACHMENT_VIDEO = 'attachment',
    EXERCISE = 'exercise',
    TEXT = 'text',
    ONLINE = 'online',
}

export const lectureUnitIcons = {
    [LectureUnitType.ATTACHMENT_VIDEO]: faDownload,
    [LectureUnitType.EXERCISE]: faQuestion,
    [LectureUnitType.TEXT]: faScroll,
    [LectureUnitType.ONLINE]: faLink,
};

export const lectureUnitTooltips = {
    [LectureUnitType.ATTACHMENT_VIDEO]: 'artemisApp.attachmentVideoUnit.tooltip',
    [LectureUnitType.EXERCISE]: '',
    [LectureUnitType.TEXT]: 'artemisApp.textUnit.tooltip',
    [LectureUnitType.ONLINE]: 'artemisApp.onlineUnit.tooltip',
};

export abstract class LectureUnit implements BaseEntity {
    public id?: number;
    public name?: string;
    public releaseDate?: dayjs.Dayjs;
    public lecture?: Lecture;
    public competencyLinks?: CompetencyLectureUnitLink[];
    public type?: LectureUnitType;
    // calculated property
    public visibleToStudents?: boolean;
    public completed?: boolean;

    protected constructor(type: LectureUnitType) {
        this.type = type;
    }
}

export function getIcon(lectureUnitType: LectureUnitType): IconProp {
    if (!lectureUnitType) {
        return faQuestion;
    }
    return lectureUnitIcons[lectureUnitType];
}

export function getIconTooltip(lectureUnitType: LectureUnitType) {
    if (!lectureUnitType) {
        return '';
    }
    return lectureUnitTooltips[lectureUnitType];
}
