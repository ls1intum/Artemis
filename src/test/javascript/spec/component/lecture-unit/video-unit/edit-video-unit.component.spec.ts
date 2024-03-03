import dayjs from 'dayjs/esm';

import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-video-unit/edit-video-unit.component';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-video-unit-form', template: '' })
class VideoUnitFormStubComponent {
    @Input() isEditMode = false;
    @Input() formData: VideoUnitFormData;
    @Output() formSubmitted: EventEmitter<VideoUnitFormData> = new EventEmitter<VideoUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content />' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('EditVideoUnitComponent', () => {
    let editVideoUnitComponentFixture: ComponentFixture<EditVideoUnitComponent>;
    let editVideoUnitComponent: EditVideoUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [VideoUnitFormStubComponent, LectureUnitLayoutStubComponent, EditVideoUnitComponent],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
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
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                editVideoUnitComponentFixture = TestBed.createComponent(EditVideoUnitComponent);
                editVideoUnitComponent = editVideoUnitComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editVideoUnitComponentFixture.detectChanges();
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
        const videoUnitFormStubComponent: VideoUnitFormStubComponent = editVideoUnitComponentFixture.debugElement.query(By.directive(VideoUnitFormStubComponent)).componentInstance;
        editVideoUnitComponentFixture.detectChanges(); // onInit
        expect(editVideoUnitComponent.videoUnit).toEqual(videoUnitOfResponse);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editVideoUnitComponent.formData.name).toEqual(videoUnitOfResponse.name);
        expect(editVideoUnitComponent.formData.releaseDate).toEqual(videoUnitOfResponse.releaseDate);
        expect(editVideoUnitComponent.formData.description).toEqual(videoUnitOfResponse.description);
        expect(editVideoUnitComponent.formData.source).toEqual(videoUnitOfResponse.source);
        expect(videoUnitFormStubComponent.formData).toEqual(editVideoUnitComponent.formData);
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

        editVideoUnitComponentFixture.detectChanges(); // onInit
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

        const textUnitForm: VideoUnitFormStubComponent = editVideoUnitComponentFixture.debugElement.query(By.directive(VideoUnitFormStubComponent)).componentInstance;
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
