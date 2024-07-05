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

describe('LearningPathLectureUnitComponent', () => {
    let component: LearningPathLectureUnitComponent;
    let fixture: ComponentFixture<LearningPathLectureUnitComponent>;

    let lectureUnitService: LectureUnitService;
    let getLectureUnitByIdSpy: jest.SpyInstance;

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

        lectureUnitService = TestBed.inject(LectureUnitService);
        getLectureUnitByIdSpy = jest.spyOn(lectureUnitService, 'getLectureUnitById').mockReturnValue(of(lectureUnit));
        lectureUnitService = TestBed.inject(LectureUnitService);

        lectureUnit.id = 1;
        lectureUnit.description = 'Example video unit';
        lectureUnit.name = 'Example video';
        lectureUnit.lecture = { id: 2 };

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
    });

    it('should get lecture unit', async () => {
        const getLectureUnitSpy = jest.spyOn(component, 'getLectureUnit');
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

    it('should set loading state correctly', async () => {
        const setIsLoadingSpy = jest.spyOn(component.isLectureUnitLoading, 'set');
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(setIsLoadingSpy).toHaveBeenCalledWith(true);
        expect(setIsLoadingSpy).toHaveBeenCalledWith(false);
    });
});
