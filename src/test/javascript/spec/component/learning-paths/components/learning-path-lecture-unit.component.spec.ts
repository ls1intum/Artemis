import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { of } from 'rxjs';
import { CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { MockComponent } from 'ng-mocks';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';

describe('LearningPathLectureUnitComponent', () => {
    let component: LearningPathLectureUnitComponent;
    let fixture: ComponentFixture<LearningPathLectureUnitComponent>;

    let learningPathNavigationService: LearningPathNavigationService;
    let lectureUnitService: LectureUnitService;
    let getLectureUnitByIdSpy: jest.SpyInstance;
    let setLearningObjectCompletionSpy: jest.SpyInstance;

    const learningPathId = 1;
    const lectureUnit: VideoUnit = {
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
        source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
    };

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
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        })
            .overrideComponent(LearningPathLectureUnitComponent, {
                remove: {
                    imports: [VideoUnitComponent, TextUnitComponent, AttachmentUnitComponent, ExerciseUnitComponent, OnlineUnitComponent],
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
        const lecture = {
            ...lectureUnit.lecture,
            course: { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED },
        };
        getLectureUnitByIdSpy.mockReturnValue(of({ ...lectureUnit, lecture }));

        fixture.detectChanges();
        await fixture.whenStable();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeFalsy();
    });
});
