import dayjs from 'dayjs/esm';
import { addPublicFilePrefix } from 'app/app.constants';
import { convertDateFromClient, convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { CompetencyLectureUnitLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import type { SimpleLecture } from 'app/openapi/model/simple-lecture';
import type { LectureDetails } from 'app/openapi/model/lecture-details';
import type { GetLectures } from 'app/openapi/model/get-lectures';
import type { LectureForOverview as GeneratedLectureForOverview } from 'app/openapi/model/lecture-for-overview';
import type { LectureUnit as GeneratedLectureUnit } from 'app/openapi/model/lecture-unit';
import type { GetLectureUnitById200Response } from 'app/openapi/model/get-lecture-unit-by-id200response';
import type { LectureSlidesAttachmentVideoUnit } from 'app/openapi/model/lecture-slides-attachment-video-unit';
import type { Attachment as GeneratedAttachment } from 'app/openapi/model/attachment';
import type { LectureDetailsAttachment } from 'app/openapi/model/lecture-details-attachment';
import type { LectureSlidesAttachment } from 'app/openapi/model/lecture-slides-attachment';
import type { Slide as GeneratedSlide } from 'app/openapi/model/slide';
import type { CompetencyLink } from 'app/openapi/model/competency-link';
import type { LectureDetailsCompetencyLink } from 'app/openapi/model/lecture-details-competency-link';
import type { TextUnit as GeneratedTextUnit } from 'app/openapi/model/text-unit';
import type { OnlineUnit as GeneratedOnlineUnit } from 'app/openapi/model/online-unit';
import type { ExerciseUnit as GeneratedExerciseUnit } from 'app/openapi/model/exercise-unit';
import type { AttachmentVideoUnit as GeneratedAttachmentVideoUnit } from 'app/openapi/model/attachment-video-unit';

/**
 * Bridges the generated models of the lecture endpoints and the lecture class graph.
 *
 * The lecture views read the class graph: dayjs dates, the unit subclasses, `linkUrl` on attachments and the access
 * flags of the course. Each response is converted once, where a service receives it, with the `to*` functions; each
 * request is built from the class graph with the `*Request` functions. Generated models carry dates as ISO strings.
 */

/** Every generated shape of a lecture unit: as a unit endpoint returns it, inside lecture details, or with its slides. */
type AnyGeneratedLectureUnit = GeneratedLectureUnit | GetLectureUnitById200Response | LectureSlidesAttachmentVideoUnit;
type AnyGeneratedAttachment = GeneratedAttachment | LectureDetailsAttachment | LectureSlidesAttachment;
type AnyGeneratedCompetencyLink = CompetencyLink | LectureDetailsCompetencyLink;
type AnyGeneratedLecture = SimpleLecture | LectureDetails | GetLectures;

const UNIT_CLASSES: Record<LectureUnitType, () => LectureUnit> = {
    [LectureUnitType.ATTACHMENT_VIDEO]: () => new AttachmentVideoUnit(),
    [LectureUnitType.EXERCISE]: () => new ExerciseUnit(),
    [LectureUnitType.ONLINE]: () => new OnlineUnit(),
    [LectureUnitType.TEXT]: () => new TextUnit(),
};

function isLectureUnitType(type: string | undefined): type is LectureUnitType {
    return type !== undefined && type in UNIT_CLASSES;
}

/**
 * Converts a generated lecture into a {@link Lecture} instance with dayjs dates and converted units.
 *
 * @param lecture any generated shape of a lecture
 * @returns the lecture; its course carries only the fields the endpoint sent
 */
export function toLecture(lecture: AnyGeneratedLecture): Lecture {
    const { course, lectureUnits, startDate, endDate, ...fields } = lecture as SimpleLecture & LectureDetails & GetLectures;
    const result = hydrate(new Lecture(), fields);
    result.startDate = convertDateStringFromServer(startDate);
    result.endDate = convertDateStringFromServer(endDate);
    if (course) {
        result.course = hydrate(new Course(), course) as Course;
    }
    if (lectureUnits) {
        result.lectureUnits = lectureUnits.map((unit) => toLectureUnit(unit));
    }
    return result;
}

/**
 * Converts a generated lecture of the course overview, keeping the narrow shape the sidebar reads.
 *
 * @param lecture the generated overview lecture
 * @returns the lecture with dayjs dates
 */
export function toLectureForOverview(lecture: GeneratedLectureForOverview): LectureForOverview {
    return {
        id: lecture.id!,
        title: lecture.title,
        startDate: convertDateStringFromServer(lecture.startDate),
        endDate: convertDateStringFromServer(lecture.endDate),
        isTutorialLecture: lecture.isTutorialLecture,
    };
}

/**
 * Converts a generated lecture unit into the matching unit subclass. The `type` the server sends selects the class.
 *
 * @param unit any generated shape of a lecture unit
 * @returns the unit with dayjs dates, its attachment with `linkUrl`, and its exercise with parsed categories
 * @throws Error if the server sent a unit type the client does not know
 */
export function toLectureUnit<T extends LectureUnit = LectureUnit>(unit: AnyGeneratedLectureUnit): T {
    if (!isLectureUnitType(unit.type)) {
        throw new Error(`Unknown lecture unit type '${unit.type}' for lecture unit ${unit.id}`);
    }
    const { releaseDate, attachment, slides, competencyLinks, exercise, lecture, ...fields } = unit as GeneratedAttachmentVideoUnit &
        LectureSlidesAttachmentVideoUnit &
        GeneratedExerciseUnit & { exercise?: object; lecture?: object };
    const result = hydrate(UNIT_CLASSES[unit.type](), fields);
    result.releaseDate = convertDateStringFromServer(releaseDate);
    if (lecture) {
        result.lecture = hydrate(new Lecture(), lecture);
    }
    if (competencyLinks) {
        result.competencyLinks = competencyLinks.map((link) => toCompetencyLink(link));
    }
    if (result instanceof AttachmentVideoUnit) {
        result.attachment = attachment ? toAttachment(attachment) : undefined;
        result.slides = slides?.map((slide) => toSlide(slide));
    }
    if (exercise && result instanceof ExerciseUnit) {
        (result as ExerciseUnit).exercise = toExercise(exercise);
    }
    return result as T;
}

/**
 * Converts a generated attachment into an {@link Attachment} with dayjs dates and its public `linkUrl`.
 *
 * @param attachment any generated shape of an attachment
 * @returns the attachment
 */
export function toAttachment(attachment: AnyGeneratedAttachment): Attachment {
    const { releaseDate, uploadDate, ...fields } = attachment as GeneratedAttachment & LectureDetailsAttachment;
    const result = hydrate(new Attachment(), fields) as Attachment;
    result.releaseDate = convertDateStringFromServer(releaseDate);
    result.uploadDate = convertDateStringFromServer(uploadDate);
    result.linkUrl = addPublicFilePrefix(result.link);
    return result;
}

function toSlide(slide: GeneratedSlide): Slide {
    const { hidden, exercise, ...fields } = slide;
    const result = hydrate(new Slide(), fields);
    result.hidden = hidden ? dayjs(hidden).toDate() : undefined;
    if (exercise) {
        result.exercise = { id: exercise.id } as Exercise;
    }
    return result;
}

function toCompetencyLink(link: AnyGeneratedCompetencyLink): CompetencyLectureUnitLink {
    const competency = link.competency ? ({ ...link.competency } as CourseCompetency) : undefined;
    return new CompetencyLectureUnitLink(competency, undefined, link.weight ?? 0, link.generatedByAi ?? false);
}

/** Converts the exercise of an exercise unit the way the hand-written services did, so the exercise row reads the same fields. */
function toExercise(exercise: object): Exercise {
    const result = ExerciseService.convertExerciseDatesFromServer(structuredClone(exercise) as unknown as Exercise)!;
    ExerciseService.parseExerciseCategories(result);
    return result;
}

/**
 * Builds the body of a lecture create or update request.
 *
 * @param lecture the edited lecture
 * @returns the request body with ISO dates and the course reduced to its id
 */
export function toSimpleLectureRequest(lecture: Lecture): SimpleLecture {
    return {
        id: lecture.id,
        title: lecture.title,
        description: lecture.description,
        startDate: convertDateFromClient(lecture.startDate),
        endDate: convertDateFromClient(lecture.endDate),
        isTutorialLecture: lecture.isTutorialLecture ?? false,
        channelName: lecture.channelName,
        course: lecture.course?.id ? { id: lecture.course.id } : undefined,
    };
}

/**
 * Builds the body of a lecture unit create or update request: the unit's fields with ISO dates and its competency
 * links reduced to what the server reads. Exercise categories are sent as the JSON strings the server stores.
 *
 * @param unit the edited unit
 * @returns the request body
 */
export function toLectureUnitRequest<T extends GeneratedTextUnit | GeneratedOnlineUnit | GeneratedExerciseUnit | GeneratedAttachmentVideoUnit>(unit: LectureUnit): T {
    const { releaseDate, competencyLinks, lecture, ...fields } = unit as LectureUnit & { exercise?: Exercise; attachment?: Attachment };
    const request: Record<string, unknown> = { ...fields, releaseDate: convertDateFromClient(releaseDate) };
    if (lecture?.id !== undefined) {
        request.lecture = { id: lecture.id };
    }
    if (competencyLinks) {
        request.competencyLinks = competencyLinks.map((link) => ({
            competency: link.competency ? { id: link.competency.id, title: link.competency.title } : undefined,
            weight: link.weight,
        }));
    }
    if (unit instanceof ExerciseUnit || unit.type === LectureUnitType.EXERCISE) {
        const exercise = (unit as ExerciseUnit).exercise;
        if (exercise) {
            const exerciseRequest = ExerciseService.convertExerciseDatesFromClient(exercise);
            ExerciseService.stringifyExerciseCategories(exerciseRequest);
            request.exercise = exerciseRequest;
        }
    }
    if ((unit as AttachmentVideoUnit).attachment) {
        request.attachment = toAttachmentRequest((unit as AttachmentVideoUnit).attachment!);
    }
    return request as T;
}

/**
 * Builds the attachment part of a multipart lecture unit request.
 *
 * @param attachment the edited attachment
 * @returns the attachment with ISO dates and without client-only fields
 */
export function toAttachmentRequest(attachment: Attachment): GeneratedAttachment {
    const { releaseDate, uploadDate, linkUrl: _linkUrl, attachmentVideoUnit: _unit, displayPageNumbers: _pages, ...fields } = attachment;
    return { ...fields, releaseDate: convertDateFromClient(releaseDate), uploadDate: convertDateFromClient(uploadDate) };
}
