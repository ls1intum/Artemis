import { Component, EventEmitter, Input, Output } from '@angular/core';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CreateVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-video-unit/create-video-unit.component';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-video-unit-form', template: '' })
class VideoUnitFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<VideoUnitFormData> = new EventEmitter<VideoUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('CreateVideoUnitComponent', () => {
    let createVideoUnitComponentFixture: ComponentFixture<CreateVideoUnitComponent>;
    let createVideoUnitComponent: CreateVideoUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [VideoUnitFormStubComponent, LectureUnitLayoutStubComponent, CreateVideoUnitComponent],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
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
        const videoUnitForm: VideoUnitFormStubComponent = createVideoUnitComponentFixture.debugElement.query(By.directive(VideoUnitFormStubComponent)).componentInstance;
        videoUnitForm.formSubmitted.emit(formDate);

        createVideoUnitComponentFixture.whenStable().then(() => {
            const videoUnitCallArgument: VideoUnit = createStub.mock.calls[0][0];
            const lectureIdCallArgument: number = createStub.mock.calls[0][1];

            expect(videoUnitCallArgument.name).toEqual(formDate.name);
            expect(videoUnitCallArgument.description).toEqual(formDate.description);
            expect(videoUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
            expect(videoUnitCallArgument.source).toEqual(formDate.source);
            expect(lectureIdCallArgument).toEqual(1);

            expect(createStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();

            navigateSpy.mockRestore();
        });
    }));
});
