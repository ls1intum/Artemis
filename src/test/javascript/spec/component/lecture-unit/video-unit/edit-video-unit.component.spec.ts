import dayjs from 'dayjs/esm';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-video-unit/edit-video-unit.component';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { VideoUnitFormComponent } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ProfileService } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';

describe('EditVideoUnitComponent', () => {
    let fixture: ComponentFixture<EditVideoUnitComponent>;
    let editVideoUnitComponent: EditVideoUnitComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ videoUnitId: 1 }),
                        },
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'videoUnitId':
                                        return 1;
                                }
                            },
                        }),
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
                            },
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
            schemas: [],
        }).compileComponents();

        fixture = TestBed.createComponent(EditVideoUnitComponent);
        editVideoUnitComponent = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(editVideoUnitComponent).not.toBeNull();
    });

    it('should set form data correctly', () => {
        const videoUnitService = TestBed.inject(VideoUnitService);

        const videoUnitOfResponse = new VideoUnit();
        videoUnitOfResponse.id = 1;
        videoUnitOfResponse.name = 'test';
        videoUnitOfResponse.releaseDate = dayjs().year(2010).month(3).date(5);
        videoUnitOfResponse.description = 'lorem ipsum';
        videoUnitOfResponse.source = 'https://www.youtube.com/embed/M7lc1UVf-VE';

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: videoUnitOfResponse,
            status: 200,
        });

        const findByIdStub = jest.spyOn(videoUnitService, 'findById').mockReturnValue(of(response));
        fixture.detectChanges();
        const videoUnitFormComponent: VideoUnitFormComponent = fixture.debugElement.query(By.directive(VideoUnitFormComponent)).componentInstance;
        fixture.detectChanges(); // onInit
        expect(editVideoUnitComponent.videoUnit).toEqual(videoUnitOfResponse);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editVideoUnitComponent.formData.name).toEqual(videoUnitOfResponse.name);
        expect(editVideoUnitComponent.formData.releaseDate).toEqual(videoUnitOfResponse.releaseDate);
        expect(editVideoUnitComponent.formData.description).toEqual(videoUnitOfResponse.description);
        expect(editVideoUnitComponent.formData.source).toEqual(videoUnitOfResponse.source);
        expect(videoUnitFormComponent.formData()).toEqual(editVideoUnitComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const videoUnitService = TestBed.inject(VideoUnitService);

        const videoUnitInDatabase: VideoUnit = new VideoUnit();
        videoUnitInDatabase.id = 1;
        videoUnitInDatabase.name = 'test';
        videoUnitInDatabase.releaseDate = dayjs().year(2010).month(3).date(5);
        videoUnitInDatabase.description = 'lorem ipsum';
        videoUnitInDatabase.source = 'https://www.youtube.com/embed/M7lc1UVf-VE';

        const findByIdResponse: HttpResponse<VideoUnit> = new HttpResponse({
            body: videoUnitInDatabase,
            status: 200,
        });
        const findByIdStub = jest.spyOn(videoUnitService, 'findById').mockReturnValue(of(findByIdResponse));

        fixture.detectChanges(); // onInit
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editVideoUnitComponent.videoUnit).toEqual(videoUnitInDatabase);

        const changedUnit: VideoUnit = {
            ...videoUnitInDatabase,
            name: 'Changed',
        };

        const updateResponse: HttpResponse<VideoUnit> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedStub = jest.spyOn(videoUnitService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const textUnitForm: VideoUnitFormComponent = fixture.debugElement.query(By.directive(VideoUnitFormComponent)).componentInstance;
        textUnitForm.formSubmitted.emit({
            name: changedUnit.name,
            description: changedUnit.description,
            releaseDate: changedUnit.releaseDate,
            source: changedUnit.source,
        });

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        navigateSpy.mockRestore();
    });
});
