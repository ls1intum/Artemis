import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import {
    toAttachmentRequest,
    toLecture,
    toLectureForOverview,
    toLectureUnit,
    toLectureUnitRequest,
    toSimpleLectureRequest,
} from 'app/lecture/shared/util/generated-lecture.util';
import type { LectureDetails } from 'app/openapi/model/lecture-details';
import type { TextUnit as GeneratedTextUnit } from 'app/openapi/model/text-unit';
import type { AttachmentVideoUnit as GeneratedAttachmentVideoUnit } from 'app/openapi/model/attachment-video-unit';
import type { ExerciseUnit as GeneratedExerciseUnit } from 'app/openapi/model/exercise-unit';

const START = '2026-10-01T08:00:00Z';
const END = '2026-10-01T10:00:00Z';
const RELEASE = '2026-09-30T12:00:00Z';

describe('generated lecture conversion', () => {
    it('converts lecture details into the class graph with dayjs dates and typed units', () => {
        const details: LectureDetails = {
            id: 3,
            title: 'Graphs',
            startDate: START,
            endDate: END,
            isTutorialLecture: false,
            course: { id: 7, title: 'Algorithms', shortName: 'algo' },
            lectureUnits: [
                { type: 'text', id: 11, name: 'Notes', releaseDate: RELEASE, content: '# Hi', completed: true, visibleToStudents: true },
                { type: 'online', id: 12, name: 'Link', source: 'https://example.org' },
                {
                    type: 'attachment',
                    id: 13,
                    name: 'Slides',
                    attachment: { id: 21, name: 'slides.pdf', link: 'attachments/21/slides.pdf', releaseDate: RELEASE, attachmentType: 'FILE' },
                },
            ],
        };

        const lecture = toLecture(details);

        expect(lecture).toBeInstanceOf(Lecture);
        expect(lecture.startDate?.isSame(dayjs(START))).toBe(true);
        expect(lecture.endDate?.isSame(dayjs(END))).toBe(true);
        expect(lecture.course).toBeInstanceOf(Course);
        expect(lecture.course?.id).toBe(7);
        const [text, online, attachmentUnit] = lecture.lectureUnits!;
        expect(text).toBeInstanceOf(TextUnit);
        expect((text as TextUnit).content).toBe('# Hi');
        expect(text.releaseDate?.isSame(dayjs(RELEASE))).toBe(true);
        expect(text.completed).toBe(true);
        expect(online).toBeInstanceOf(OnlineUnit);
        expect(attachmentUnit).toBeInstanceOf(AttachmentVideoUnit);
        const attachment = (attachmentUnit as AttachmentVideoUnit).attachment!;
        expect(attachment).toBeInstanceOf(Attachment);
        expect(attachment.releaseDate?.isSame(dayjs(RELEASE))).toBe(true);
        expect(attachment.linkUrl).toBe('api/core/files/attachments/21/slides.pdf');
    });

    it('keeps absent dates absent', () => {
        const lecture = toLecture({ id: 1, title: 'No dates' });
        expect(lecture.startDate).toBeUndefined();
        expect(lecture.endDate).toBeUndefined();
        expect(lecture.lectureUnits).toBeUndefined();
    });

    it('converts an overview lecture into the narrow sidebar shape', () => {
        const lecture = toLectureForOverview({ id: 4, title: 'Trees', startDate: START, isTutorialLecture: true });
        expect(lecture).toEqual({ id: 4, title: 'Trees', startDate: dayjs(START), endDate: undefined, isTutorialLecture: true });
    });

    it('converts competency links and the hidden date of slides', () => {
        const generated: GeneratedAttachmentVideoUnit = {
            type: 'attachment',
            id: 13,
            competencyLinks: [{ competency: { id: 5, title: 'Sorting' }, weight: 0.5, generatedByAi: true }],
            slides: [{ id: 1, slideNumber: 2, hidden: RELEASE, exercise: { id: 9 } }],
        };

        const unit = toLectureUnit<AttachmentVideoUnit>(generated);

        const link = unit.competencyLinks![0];
        expect(link).toBeInstanceOf(CompetencyLectureUnitLink);
        expect(link.competency).toEqual({ id: 5, title: 'Sorting' });
        expect(link.weight).toBe(0.5);
        expect(link.generatedByAi).toBe(true);
        expect(unit.slides![0].hidden).toEqual(new Date(RELEASE));
        expect(unit.slides![0].exercise?.id).toBe(9);
    });

    it('parses the categories and dates of the exercise of an exercise unit', () => {
        const generated: GeneratedExerciseUnit = {
            type: 'exercise',
            id: 14,
            exercise: { id: 30, title: 'Sort', releaseDate: RELEASE, type: 'text' },
        };
        (generated.exercise as Record<string, unknown>).categories = ['{"category":"easy","color":"#00ff00"}'];

        const unit = toLectureUnit<ExerciseUnit>(generated);

        expect(unit).toBeInstanceOf(ExerciseUnit);
        expect(unit.exercise?.releaseDate?.isSame(dayjs(RELEASE))).toBe(true);
        expect(unit.exercise?.categories).toEqual([new ExerciseCategory('easy', '#00ff00')]);
    });

    it('rejects a unit type the client does not know', () => {
        expect(() => toLectureUnit({ type: 'video', id: 99 } as GeneratedTextUnit)).toThrow("Unknown lecture unit type 'video'");
    });

    it('sends a lecture with ISO dates and the course as its id', () => {
        const lecture = new Lecture();
        lecture.id = 3;
        lecture.title = 'Graphs';
        lecture.startDate = dayjs(START);
        lecture.course = new Course();
        lecture.course.id = 7;
        lecture.course.title = 'Algorithms';
        lecture.channelName = 'lecture-graphs';

        expect(toSimpleLectureRequest(lecture)).toEqual({
            id: 3,
            title: 'Graphs',
            description: undefined,
            startDate: dayjs(START).toJSON(),
            endDate: undefined,
            isTutorialLecture: false,
            channelName: 'lecture-graphs',
            course: { id: 7 },
        });
    });

    it('sends a unit with an ISO release date, the lecture as its id and links as competency and weight', () => {
        const unit = new TextUnit();
        unit.id = 11;
        unit.name = 'Notes';
        unit.content = '# Hi';
        unit.releaseDate = dayjs(RELEASE);
        unit.lecture = new Lecture();
        unit.lecture.id = 3;
        unit.lecture.title = 'Graphs';
        unit.competencyLinks = [new CompetencyLectureUnitLink({ id: 5, title: 'Sorting' } as never, unit, 1)];

        expect(toLectureUnitRequest<GeneratedTextUnit>(unit)).toEqual({
            id: 11,
            name: 'Notes',
            content: '# Hi',
            type: LectureUnitType.TEXT,
            releaseDate: dayjs(RELEASE).toJSON(),
            lecture: { id: 3 },
            competencyLinks: [{ competency: { id: 5, title: 'Sorting' }, weight: 1 }],
        });
    });

    it('sends the exercise of an exercise unit with its categories as JSON strings', () => {
        const unit = new ExerciseUnit();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 30;
        exercise.categories = [new ExerciseCategory('easy', '#00ff00')];
        unit.exercise = exercise;

        const request = toLectureUnitRequest<GeneratedExerciseUnit>(unit);

        expect((request.exercise as Record<string, unknown>).categories).toEqual([JSON.stringify(new ExerciseCategory('easy', '#00ff00'))]);
        expect(exercise.categories).toEqual([new ExerciseCategory('easy', '#00ff00')]);
    });

    it('sends an attachment without its client-only fields', () => {
        const attachment = new Attachment();
        attachment.id = 21;
        attachment.name = 'slides.pdf';
        attachment.link = 'attachments/21/slides.pdf';
        attachment.linkUrl = 'api/core/files/attachments/21/slides.pdf';
        attachment.releaseDate = dayjs(RELEASE);
        attachment.attachmentType = AttachmentType.FILE;

        expect(toAttachmentRequest(attachment)).toEqual({
            id: 21,
            name: 'slides.pdf',
            link: 'attachments/21/slides.pdf',
            releaseDate: dayjs(RELEASE).toJSON(),
            uploadDate: undefined,
            attachmentType: 'FILE',
        });
    });
});
