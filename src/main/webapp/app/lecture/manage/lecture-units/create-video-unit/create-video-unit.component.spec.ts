import { VideoUnitFormComponent, VideoUnitFormData } from 'app/lecture/manage/lecture-units/video-unit-form/video-unit-form.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CreateVideoUnitComponent } from 'app/lecture/manage/lecture-units/create-video-unit/create-video-unit.component';
import { VideoUnitService } from 'app/lecture/manage/lecture-units/services/videoUnit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { VideoUnit } from 'app/lecture/shared/entities/lecture-unit/videoUnit.model';
import dayjs from 'dayjs/esm';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from '../../../../core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('CreateVideoUnitComponent', () => {
    let createVideoUnitComponentFixture: ComponentFixture<CreateVideoUnitComponent>;
    let createVideoUnitComponent: CreateVideoUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(AlertService),
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
});
