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

describe('LearningPathLectureUnitComponent', () => {
    let component: LearningPathLectureUnitComponent;
    let fixture: ComponentFixture<LearningPathLectureUnitComponent>;

    // TODO: Activate tests when lecture unit methods are migrated to using Promises
    // let lectureUnitService: LectureUnitService;
    // let learningPathNavigationService: LearningPathNavigationService;
    // let getLectureUnitSpy: jest.SpyInstance;

    const learningPathId = 1;
    const lectureUnit = new VideoUnit();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathLectureUnitComponent],
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

        // TODO: Activate tests when lecture unit methods are migrated to using Promises
        // learningPathNavigationService = TestBed.inject(LearningPathNavigationService);
        // getLectureUnitSpy = jest.spyOn(lectureUnitService, 'getLectureUnitById').mockReturnValue(of(lectureUnit));
        // lectureUnitService = TestBed.inject(LectureUnitService);

        lectureUnit.id = 1;
        lectureUnit.description = 'Example video unit';
        lectureUnit.name = 'Example video';
        lectureUnit.lecture = { id: 2 };

        fixture = TestBed.createComponent(LearningPathLectureUnitComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('lectureUnitId', 1);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.lectureUnitId()).toBe(learningPathId);
    });

    // TODO: Activate tests when lecture unit methods are migrated to using Promises
    // it('should get lecture unit', async () => {
    //     fixture.detectChanges();
    //     await fixture.whenStable();
    //     fixture.detectChanges();
    //
    //     expect(component.lectureUnit()).toEqual(lectureUnit);
    //     expect(getLectureUnitSpy).toHaveBeenCalledWith(learningPathId);
    // });
    //
    // it('should call lecture unit service on completion', async () => {
    //     const lectureCompletionSpy = jest.spyOn(lectureUnitService, 'completeLectureUnit').mockReturnValue();
    //     const setCurrentLearningObjectCompletionSpy = jest.spyOn(learningPathNavigationService, 'setCurrentLearningObjectCompletion');
    //
    //     fixture.detectChanges();
    //     await fixture.whenStable();
    //
    //     component.setLearningObjectCompletion({ lectureUnit: component.lectureUnit()!, completed: true });
    //     expect(lectureCompletionSpy).toHaveBeenCalledOnce();
    //     expect(setCurrentLearningObjectCompletionSpy).toHaveBeenCalledWith(true);
    // });
    //
    // it('should set isLoading correctly on load', async () => {
    //     const setIsLoadingSpy = jest.spyOn(component.isLectureUnitLoading, 'set');
    //
    //     fixture.detectChanges();
    //     await fixture.whenStable();
    //
    //     expect(setIsLoadingSpy).toHaveBeenCalledWith(true);
    //     expect(setIsLoadingSpy).toHaveBeenCalledWith(false);
    // });
});
