import { LearningPathLectureUnitComponent } from 'app/atlas/overview/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { of } from 'rxjs';
import { CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { MockComponent, MockInstance } from 'ng-mocks';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { ElementRef, signal } from '@angular/core';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

describe('LearningPathLectureUnitComponent', () => {
    let component: LearningPathLectureUnitComponent;
    let fixture: ComponentFixture<LearningPathLectureUnitComponent>;

    let learningPathNavigationService: LearningPathNavigationService;
    let lectureUnitService: LectureUnitService;
    let getLectureUnitByIdSpy: jest.SpyInstance;
    let setLearningObjectCompletionSpy: jest.SpyInstance;

    const learningPathId = 1;
    const lectureUnit: AttachmentVideoUnit = {
        id: 1,
        description: 'Example video unit',
        name: 'Example video',
        lecture: {
            id: 2,
            course: {
                courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
            },
        },
        completed: false,
        visibleToStudents: true,
        videoSource: 'https://www.youtube.com/embed/8iU8LPEa4o0',
    };

    MockInstance(DiscussionSectionComponent, 'content', signal(new ElementRef(document.createElement('div'))));
    MockInstance(DiscussionSectionComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
    // @ts-ignore
    MockInstance(DiscussionSectionComponent, 'postCreateEditModal', signal(new ElementRef(document.createElement('div'))));

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathLectureUnitComponent, MockComponent(DiscussionSectionComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                LocalStorageService,
                SessionStorageService,
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        })
            .overrideComponent(LearningPathLectureUnitComponent, {
                remove: {
                    imports: [TextUnitComponent, AttachmentVideoUnitComponent, ExerciseUnitComponent, OnlineUnitComponent],
                },
            })
            .compileComponents();

        lectureUnitService = TestBed.inject(LectureUnitService);
        getLectureUnitByIdSpy = jest.spyOn(lectureUnitService, 'getLectureUnitById').mockReturnValue(of(lectureUnit));
        lectureUnitService = TestBed.inject(LectureUnitService);
        learningPathNavigationService = TestBed.inject(LearningPathNavigationService);
        setLearningObjectCompletionSpy = jest.spyOn(learningPathNavigationService, 'setCurrentLearningObjectCompletion').mockReturnValue();

        fixture = TestBed.createComponent(LearningPathLectureUnitComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('lectureUnitId', learningPathId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.lectureUnitId()).toBe(learningPathId);
        expect(component.isCommunicationEnabled()).toBeFalse();
    });

    it('should get lecture unit', async () => {
        const getLectureUnitSpy = jest.spyOn(component, 'loadLectureUnit');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(getLectureUnitSpy).toHaveBeenCalledWith(learningPathId);
        expect(getLectureUnitByIdSpy).toHaveBeenCalledWith(learningPathId);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(component.lectureUnit()).toEqual(lectureUnit);
    });

    it('should not set current learning object on error', async () => {
        const completeLectureUnitSpy = jest.spyOn(lectureUnitService, 'completeLectureUnit').mockImplementationOnce(() => {});

        fixture.detectChanges();
        await fixture.whenStable();

        component.setLearningObjectCompletion({ completed: true, lectureUnit: lectureUnit });

        expect(completeLectureUnitSpy).toHaveBeenCalledExactlyOnceWith(lectureUnit.lecture, {
            completed: true,
            lectureUnit: lectureUnit,
        });
        expect(setLearningObjectCompletionSpy).not.toHaveBeenCalled();
    });

    it('should set current learning object completion', async () => {
        const completeLectureUnitSpy = jest
            .spyOn(lectureUnitService, 'completeLectureUnit')
            .mockImplementationOnce((lecture: Lecture, completionEvent: LectureUnitCompletionEvent) => (completionEvent.lectureUnit.completed = completionEvent.completed));

        fixture.detectChanges();
        await fixture.whenStable();

        component.setLearningObjectCompletion({ completed: true, lectureUnit: lectureUnit });

        expect(completeLectureUnitSpy).toHaveBeenCalledExactlyOnceWith(lectureUnit.lecture, {
            completed: true,
            lectureUnit: lectureUnit,
        });
        expect(setLearningObjectCompletionSpy).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should set loading state correctly', async () => {
        const setIsLoadingSpy = jest.spyOn(component.isLoading, 'set');
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(setIsLoadingSpy).toHaveBeenCalledWith(true);
        expect(setIsLoadingSpy).toHaveBeenCalledWith(false);
    });

    it('should show discussion section when communication is enabled', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeTruthy();
    });

    it('should not show discussion section when communication is disabled', async () => {
        const lecture = Object.assign({}, lectureUnit.lecture, { course: { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED } });
        getLectureUnitByIdSpy.mockReturnValue(of(Object.assign({}, lectureUnit, { lecture })));

        fixture.detectChanges();
        await fixture.whenStable();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeFalsy();
    });
});
