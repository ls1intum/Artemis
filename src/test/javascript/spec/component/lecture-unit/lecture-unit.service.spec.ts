import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { ArtemisTestModule } from '../../test.module';

describe('LectureUnitService', () => {
    let service: LectureUnitService;
    let httpMock: HttpTestingController;
    let exerciseUnit: ExerciseUnit;
    let attachmentUnit: AttachmentUnit;
    let textUnit: TextUnit;
    let videoUnit: VideoUnit;
    let lecture: Lecture;
    let expectedResultArray: any;
    let convertDateFromServerEntitySpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
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

        attachmentUnit = new AttachmentUnit();
        attachmentUnit.id = 37;
        attachmentUnit.description = 'Lorem Ipsum Attachment';
        attachmentUnit.attachment = attachment;

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

        videoUnit = new VideoUnit();
        videoUnit.id = 15;
        videoUnit.description = 'Lorem Ipsum Video';
        videoUnit.source = 'test';
        videoUnit.releaseDate = dayjs().year(2011).month(3).date(9);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should receive updated order array', fakeAsync(() => {
        convertDateFromServerEntitySpy = jest.spyOn(service, 'convertLectureUnitDateFromServer');
        const orderArray = [videoUnit, attachmentUnit, textUnit, exerciseUnit];
        service.updateOrder(1, orderArray).subscribe((resp) => (expectedResultArray = resp));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(orderArray);
        expect(expectedResultArray.body).toBeArrayOfSize(4);
        expect(convertDateFromServerEntitySpy).toHaveBeenCalledTimes(4);
    }));

    it('should get title of associated element', async () => {
        expect(service.getLectureUnitName(attachmentUnit)).toEqual(attachmentUnit.attachment!.name);
        expect(service.getLectureUnitName(exerciseUnit)).toEqual(exerciseUnit.exercise!.title);
        expect(service.getLectureUnitName(textUnit)).toEqual(textUnit.name);
        expect(service.getLectureUnitName(videoUnit)).toEqual(videoUnit.name);
    });

    it('should get release date of associated element', async () => {
        expect(service.getLectureUnitReleaseDate(attachmentUnit)).toEqual(attachmentUnit.attachment!.releaseDate);
        expect(service.getLectureUnitReleaseDate(exerciseUnit)).toEqual(exerciseUnit.exercise!.releaseDate);
        expect(service.getLectureUnitReleaseDate(textUnit)).toEqual(textUnit.releaseDate);
        expect(service.getLectureUnitReleaseDate(videoUnit)).toEqual(videoUnit.releaseDate);
    });

    it('should send a request to the server to get ngx representation of learning path', fakeAsync(() => {
        service.getLectureUnitForLearningPathNodeDetails(1).subscribe();
        httpMock.expectOne({ method: 'GET', url: 'api/lecture-units/1/for-learning-path-node-details' });
    }));

    it('should set lecture unit as completed', fakeAsync(() => {
        exerciseUnit.completed = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: true });
        httpMock.expectOne({ method: 'POST', url: 'api/lectures/5/lecture-units/42/completion?completed=true' }).flush(null);
        expect(exerciseUnit.completed).toBeTrue();
    }));

    it('should set lecture unit as uncompleted', fakeAsync(() => {
        exerciseUnit.completed = true;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectOne({ method: 'POST', url: 'api/lectures/5/lecture-units/42/completion?completed=false' }).flush(null);
        expect(exerciseUnit.completed).toBeFalse();
    }));

    it('should not set completion status if already completed', fakeAsync(() => {
        exerciseUnit.completed = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectNone({ method: 'POST', url: 'api/lectures/5/lecture-units/42/completion?completed=false' });
    }));

    it('should not set completion status if not visible', fakeAsync(() => {
        exerciseUnit.visibleToStudents = false;
        service.completeLectureUnit(lecture, { lectureUnit: exerciseUnit, completed: false });
        httpMock.expectNone({ method: 'POST', url: 'api/lectures/5/lecture-units/42/completion?completed=false' });
    }));
});
