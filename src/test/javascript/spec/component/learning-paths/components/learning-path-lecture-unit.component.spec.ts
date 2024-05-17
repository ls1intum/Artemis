import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service.ts';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

describe('LearningPathLectureUnitComponent', () => {
    let component: LearningPathLectureUnitComponent;
    let fixture: ComponentFixture<LearningPathLectureUnitComponent>;
    let lectureUnitService: LectureUnitService;

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
            ],
        })
            .overrideComponent(LearningPathLectureUnitComponent, {
                remove: {
                    imports: [VideoUnitComponent, TextUnitComponent, AttachmentUnitComponent, ExerciseUnitComponent, OnlineUnitComponent],
                },
            })
            .compileComponents();

        lectureUnitService = TestBed.inject(LectureUnitService);

        fixture = TestBed.createComponent(LearningPathLectureUnitComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('lectureUnitId', 1);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.lectureUnitId()).toBe(1);
    });

    it('should get lecture unit', fakeAsync(() => {
        const lectureUnit = new VideoUnit();
        lectureUnit.id = 1;
        lectureUnit.description = 'Example video unit';
        lectureUnit.name = 'Example video';
        lectureUnit.link = '/path/to/video/test.mp4';

        const httpResponse = new HttpResponse({ body: lectureUnit });
        const getLectureUnitSpy = jest.spyOn(lectureUnitService, 'getLectureUnitById').mockReturnValue(of(httpResponse));

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(component.lectureUnit()).toEqual(lectureUnit);
        expect(getLectureUnitSpy).toHaveBeenCalledOnce(1);
    }));
});
