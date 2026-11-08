import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitService, ProcessingPhase, toProcessingStatus, toTranscriptionStatus } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { Course } from 'app/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('LectureUnitService', () => {
    let service: LectureUnitService;
    let httpMock: HttpTestingController;
    let exerciseUnit: ExerciseUnit;
    let attachmentVideoUnit: AttachmentVideoUnit;
    let textUnit: TextUnit;
    let lecture: Lecture;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(LectureUnitService);
        httpMock = TestBed.inject(HttpTestingController);

        const attachment = new Attachment();
        attachment.id = 3;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.uploadDate = dayjs().year(2010).month(3).date(8);
        attachment.name = 'Example attachment';
        attachment.link = '/path/to/file/test.pdf';

        attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = 37;
        attachmentVideoUnit.name = 'Example attachment video unit';
        attachmentVideoUnit.description = 'Lorem Ipsum Attachment';
        attachmentVideoUnit.attachment = attachment;

        const course = new Course();
        const exercise = new TextExercise(course, undefined);
        exercise.id = 1;
        exercise.shortName = 'Example exercise';

        exerciseUnit = new ExerciseUnit();
        exerciseUnit.id = 42;
        exerciseUnit.exercise = exercise;
        exerciseUnit.visibleToStudents = true;

        lecture = {
            id: 5,
            lectureUnits: [exerciseUnit],
        };

        textUnit = new TextUnit();
        textUnit.id = 23;
        textUnit.content = 'Lorem Ipsum Text';
        textUnit.releaseDate = dayjs().year(2011).month(3).date(1);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get title of associated element', async () => {
        expect(service.getLectureUnitName(attachmentVideoUnit)).toEqual(attachmentVideoUnit.name);
        expect(service.getLectureUnitName(exerciseUnit)).toEqual(exerciseUnit.exercise!.title);
        expect(service.getLectureUnitName(textUnit)).toEqual(textUnit.name);
    });

    it('should get release date of associated element', async () => {
        expect(service.getLectureUnitReleaseDate(attachmentVideoUnit)).toEqual(attachmentVideoUnit.attachment!.releaseDate);
        expect(service.getLectureUnitReleaseDate(exerciseUnit)).toEqual(exerciseUnit.exercise!.releaseDate);
        expect(service.getLectureUnitReleaseDate(textUnit)).toEqual(textUnit.releaseDate);
    });

    it('should set lecture unit as completed', async () => {
        exerciseUnit.completed = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: true });
        httpMock.expectOne({ method: 'POST', url: '/api/lecture/lectures/5/lecture-units/42/completion?completed=true' }).flush(null);
        expect(exerciseUnit.completed).toBe(true);
    });

    it('should set lecture unit as uncompleted', async () => {
        exerciseUnit.completed = true;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectOne({ method: 'POST', url: '/api/lecture/lectures/5/lecture-units/42/completion?completed=false' }).flush(null);
        expect(exerciseUnit.completed).toBe(false);
    });

    it('should invoke the success callback after a completed request', async () => {
        exerciseUnit.completed = false;
        const onSuccess = vi.fn();
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: true }, onSuccess);
        httpMock.expectOne({ method: 'POST', url: '/api/lecture/lectures/5/lecture-units/42/completion?completed=true' }).flush(null);
        expect(exerciseUnit.completed).toBe(true);
        expect(onSuccess).toHaveBeenCalledOnce();
    });

    it('should not invoke the success callback when the request is skipped', async () => {
        exerciseUnit.completed = false;
        const onSuccess = vi.fn();
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false }, onSuccess);
        httpMock.expectNone({ method: 'POST', url: '/api/lecture/lectures/5/lecture-units/42/completion?completed=false' });
        expect(onSuccess).not.toHaveBeenCalled();
    });

    it('should not set completion status if already completed', async () => {
        exerciseUnit.completed = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectNone({ method: 'POST', url: '/api/lecture/lectures/5/lecture-units/42/completion?completed=false' });
    });

    it('should not set completion status if not visible', async () => {
        exerciseUnit.visibleToStudents = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectNone({ method: 'POST', url: '/api/lecture/lectures/5/lecture-units/42/completion?completed=false' });
    });
});

describe('processing status mapping', () => {
    it('should map a combined status to the processing status of the unit', () => {
        const status = toProcessingStatus({
            lectureUnitId: 7,
            processingPhase: 'INGESTING',
            retryCount: 2,
            startedAt: '2026-07-15T13:39:00Z',
            processingErrorKey: 'some.key',
            stageName: 'embedding',
            stageProgress: 41,
            stageTotal: 142,
            lastHeartbeatAt: '2026-07-15T13:40:00Z',
        });

        expect(status).toMatchObject({
            lectureUnitId: 7,
            phase: ProcessingPhase.INGESTING,
            retryCount: 2,
            startedAt: '2026-07-15T13:39:00Z',
            errorKey: 'some.key',
            stageName: 'embedding',
            stageProgress: 41,
            stageTotal: 142,
            lastHeartbeatAt: '2026-07-15T13:40:00Z',
        });
        expect(status?.receivedAt).toBeTypeOf('number');
    });

    it('should not map a combined status that names no lecture unit', () => {
        expect(toProcessingStatus({ processingPhase: 'DONE', retryCount: 0 })).toBeUndefined();
    });

    it('should map the transcription status to the client enum, and a missing one to undefined', () => {
        expect(toTranscriptionStatus({ lectureUnitId: 7, transcriptionStatus: 'COMPLETED' })).toBe(TranscriptionStatus.COMPLETED);
        expect(toTranscriptionStatus({ lectureUnitId: 7 })).toBeUndefined();
    });
});
