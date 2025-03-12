import { VideoUnitFormComponent, VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CreateVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-video-unit/create-video-unit.component';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import dayjs from 'dayjs/esm';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { LectureService } from 'app/lecture/lecture.secrvice';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';

describe('CreateVideoUnitComponent', () => {
    let createVideoUnitComponentFixture: ComponentFixture<CreateVideoUnitComponent>;
    let createVideoUnitComponent: CreateVideoUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(AlertService),
                MockProvider(LectureService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'lectureId':
                                                return 1;
                                        }
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                        },
                                    }),
                                },
                            },
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                createVideoUnitComponentFixture = TestBed.createComponent(CreateVideoUnitComponent);
                createVideoUnitComponent = createVideoUnitComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        createVideoUnitComponentFixture.detectChanges();
        expect(createVideoUnitComponent).not.toBeNull();
    });

    it('should send POST request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const videoUnitService = TestBed.inject(VideoUnitService);

        const formDate: VideoUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
        };

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: new VideoUnit(),
            status: 201,
        });

        const createStub = jest.spyOn(videoUnitService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createVideoUnitComponentFixture.detectChanges();
        tick();
        const videoUnitForm: VideoUnitFormComponent = createVideoUnitComponentFixture.debugElement.query(By.directive(VideoUnitFormComponent)).componentInstance;
        videoUnitForm.formSubmitted.emit(formDate);

        createVideoUnitComponentFixture.whenStable().then(() => {
            const videoUnitCallArgument: VideoUnit = createStub.mock.calls[0][0];
            const lectureIdCallArgument: number = createStub.mock.calls[0][1];

            expect(videoUnitCallArgument.name).toEqual(formDate.name);
            expect(videoUnitCallArgument.description).toEqual(formDate.description);
            expect(videoUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
            expect(videoUnitCallArgument.source).toEqual(formDate.source);
            expect(lectureIdCallArgument).toBe(1);

            expect(createStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();

            navigateSpy.mockRestore();
        });
    }));

    it('should provide all attachment units', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);

        const attachmentUnit1 = new AttachmentUnit();
        attachmentUnit1.id = 1;

        const attachmentUnit2 = new AttachmentUnit();
        attachmentUnit2.id = 2;

        const textUnit1 = new TextUnit();
        textUnit1.id = 3;

        const lecture = new Lecture();
        lecture.lectureUnits = [attachmentUnit1, attachmentUnit2, textUnit1];

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: lecture,
            status: 201,
        });

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(response));

        createVideoUnitComponentFixture.detectChanges();
        tick();

        expect(createVideoUnitComponent.availableAttachmentUnits.length).toBe(3);
        expect(createVideoUnitComponent.availableAttachmentUnits).toEqual([{}, attachmentUnit1, attachmentUnit2]);
    }));

    it('should provide all attachment units expect attachment unit a attached to a different video unit', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);

        const attachmentUnit1 = new AttachmentUnit();
        attachmentUnit1.id = 1;

        const videoUnit = new VideoUnit();
        videoUnit.id = 5;

        const attachmentUnit2 = new AttachmentUnit();
        attachmentUnit2.id = 2;
        attachmentUnit2.correspondingVideoUnit = videoUnit;

        const textUnit1 = new TextUnit();
        textUnit1.id = 4;

        const lecture = new Lecture();
        lecture.lectureUnits = [attachmentUnit1, attachmentUnit2, textUnit1];

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: lecture,
            status: 201,
        });

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(response));

        createVideoUnitComponentFixture.detectChanges();
        tick();

        expect(createVideoUnitComponent.availableAttachmentUnits.length).toBe(2);
        expect(createVideoUnitComponent.availableAttachmentUnits).toEqual([{}, attachmentUnit1]);
    }));
});
