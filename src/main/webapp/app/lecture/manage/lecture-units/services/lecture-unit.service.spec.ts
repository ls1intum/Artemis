import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { Course } from 'app/core/course/shared/entities/course.model';
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
    let expectedResultArray: any;
    let convertDateFromServerEntitySpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        expectedResultArray = {} as HttpResponse<LectureUnit[]>;
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

    it('should receive updated order array', fakeAsync(() => {
        convertDateFromServerEntitySpy = jest.spyOn(service, 'convertLectureUnitDateFromServer');
        const orderArray = [attachmentVideoUnit, textUnit, exerciseUnit];
        service.updateOrder(1, orderArray).subscribe((resp) => (expectedResultArray = resp));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(orderArray);
        expect(expectedResultArray.body).toBeArrayOfSize(3);
        expect(convertDateFromServerEntitySpy).toHaveBeenCalledTimes(3);
    }));

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

    it('should send a request to the server to get ngx representation of learning path', fakeAsync(() => {
        service.getLectureUnitForLearningPathNodeDetails(1).subscribe();
        httpMock.expectOne({ method: 'GET', url: 'api/lecture/lecture-units/1/for-learning-path-node-details' });
    }));

    it('should set lecture unit as completed', fakeAsync(() => {
        exerciseUnit.completed = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: true });
        httpMock.expectOne({ method: 'POST', url: 'api/lecture/lectures/5/lecture-units/42/completion?completed=true' }).flush(null);
        expect(exerciseUnit.completed).toBeTrue();
    }));

    it('should set lecture unit as uncompleted', fakeAsync(() => {
        exerciseUnit.completed = true;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectOne({ method: 'POST', url: 'api/lecture/lectures/5/lecture-units/42/completion?completed=false' }).flush(null);
        expect(exerciseUnit.completed).toBeFalse();
    }));

    it('should not set completion status if already completed', fakeAsync(() => {
        exerciseUnit.completed = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectNone({ method: 'POST', url: 'api/lecture/lectures/5/lecture-units/42/completion?completed=false' });
    }));

    it('should not set completion status if not visible', fakeAsync(() => {
        exerciseUnit.visibleToStudents = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectNone({ method: 'POST', url: 'api/lecture/lectures/5/lecture-units/42/completion?completed=false' });
    }));

    it('should handle empty response body when converting dates from server on response array', fakeAsync(() => {
        const convertDateFromServerEntitySpy = jest.spyOn(service, 'convertLectureUnitDateFromServer');
        const emptyResponse = new HttpResponse<LectureUnit[]>({ body: null });

        const result = service.convertLectureUnitResponseArrayDatesFromServer(emptyResponse);

        expect(convertDateFromServerEntitySpy).not.toHaveBeenCalled();
        expect(result).toBe(emptyResponse);
    }));
});
