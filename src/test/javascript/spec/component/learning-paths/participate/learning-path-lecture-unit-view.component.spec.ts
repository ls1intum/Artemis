import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockModule } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { RouterModule } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LearningPathLectureUnitViewComponent, LectureUnitCompletionEvent } from 'app/course/learning-paths/participate/lecture-unit/learning-path-lecture-unit-view.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';

describe('LearningPathLectureUnitViewComponent', () => {
    let fixture: ComponentFixture<LearningPathLectureUnitViewComponent>;
    let comp: LearningPathLectureUnitViewComponent;
    let lecture: Lecture;
    let lectureUnitService: LectureUnitService;
    let setCompletionStub: jest.SpyInstance;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(RouterModule), MockModule(ArtemisLectureUnitsModule)],
            declarations: [LearningPathLectureUnitViewComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathLectureUnitViewComponent);
                comp = fixture.componentInstance;
                lecture = new Lecture();
                lecture.id = 1;
                lectureUnitService = TestBed.inject(LectureUnitService);
                setCompletionStub = jest.spyOn(lectureUnitService, 'setCompletion');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display attachment unit correctly', () => {
        const attachment = new AttachmentUnit();
        attachment.id = 3;
        lecture.lectureUnits = [attachment];
        comp.lecture = lecture;
        comp.lectureUnit = attachment;
        fixture.detectChanges();
        const view = fixture.debugElement.nativeElement.querySelector('jhi-attachment-unit');
        expect(view).toBeTruthy();
    });

    it('should display video unit correctly', () => {
        const video = new VideoUnit();
        video.id = 3;
        lecture.lectureUnits = [video];
        comp.lecture = lecture;
        comp.lectureUnit = video;
        fixture.detectChanges();
        const view = fixture.debugElement.nativeElement.querySelector('jhi-video-unit');
        expect(view).toBeTruthy();
    });

    it('should display text unit correctly', () => {
        const text = new TextUnit();
        text.id = 3;
        lecture.lectureUnits = [text];
        comp.lecture = lecture;
        comp.lectureUnit = text;
        fixture.detectChanges();
        const view = fixture.debugElement.nativeElement.querySelector('jhi-text-unit');
        expect(view).toBeTruthy();
    });

    it('should display online unit correctly', () => {
        const online = new OnlineUnit();
        online.id = 3;
        lecture.lectureUnits = [online];
        comp.lecture = lecture;
        comp.lectureUnit = online;
        fixture.detectChanges();
        const view = fixture.debugElement.nativeElement.querySelector('jhi-online-unit');
        expect(view).toBeTruthy();
    });

    it('should display no discussions when disabled', () => {
        const attachment = new AttachmentUnit();
        attachment.id = 3;
        lecture.lectureUnits = [attachment];
        comp.lecture = lecture;
        comp.lectureUnit = attachment;
        lecture.course = new Course();
        lecture.course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        fixture.detectChanges();
        const outlet = fixture.debugElement.nativeElement.querySelector('router-outlet');
        expect(outlet).toBeFalsy();
    });

    it('should display discussions when enabled', () => {
        const attachment = new AttachmentUnit();
        attachment.id = 3;
        lecture.lectureUnits = [attachment];
        comp.lecture = lecture;
        comp.lectureUnit = attachment;
        lecture.course = new Course();
        lecture.course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        fixture.detectChanges();
        const outlet = fixture.debugElement.nativeElement.querySelector('router-outlet');
        expect(outlet).toBeTruthy();
    });

    it('should set lecture unit completion', () => {
        const attachment = new AttachmentUnit();
        attachment.id = 3;
        attachment.visibleToStudents = true;
        attachment.completed = false;
        lecture.lectureUnits = [attachment];
        comp.lecture = lecture;
        comp.lectureUnit = attachment;
        lecture.course = new Course();
        fixture.detectChanges();
        const event = { lectureUnit: attachment, completed: true } as LectureUnitCompletionEvent;
        comp.completeLectureUnit(event);
        expect(setCompletionStub).toHaveBeenCalledOnce();
        expect(setCompletionStub).toHaveBeenCalledWith(attachment.id, lecture.id, event.completed);
    });

    it('should set properties of child on activate', () => {
        const attachment = new AttachmentUnit();
        attachment.id = 3;
        lecture.lectureUnits = [attachment];
        comp.lecture = lecture;
        comp.lectureUnit = attachment;
        lecture.course = new Course();
        fixture.detectChanges();
        const instance = { lecture: undefined, isCommunicationPage: undefined } as DiscussionSectionComponent;
        comp.onChildActivate(instance);
        expect(instance.lecture).toEqual(lecture);
        expect(instance.isCommunicationPage).toBeFalsy();
    });
});
