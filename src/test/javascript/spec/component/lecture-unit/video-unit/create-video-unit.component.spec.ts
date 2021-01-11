import { Component, EventEmitter, Input, Output } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CreateVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-video-unit/create-video-unit.component';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockProvider } from 'ng-mocks';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Observable, of } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

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
    const sandbox = sinon.createSandbox();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [VideoUnitFormStubComponent, LectureUnitLayoutStubComponent, CreateVideoUnitComponent],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(JhiAlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: Observable.of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'lectureId':
                                                return 1;
                                        }
                                    },
                                }),
                                parent: {
                                    paramMap: Observable.of({
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

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        createVideoUnitComponentFixture.detectChanges();
        expect(createVideoUnitComponent).to.be.ok;
    });

    it('should send POST request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const videoUnitService = TestBed.inject(VideoUnitService);

        const formDate: VideoUnitFormData = {
            name: 'Test',
            releaseDate: moment({ years: 2010, months: 3, date: 5 }),
            description: 'Lorem Ipsum',
            source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
        };

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: new VideoUnit(),
            status: 201,
        });

        const createStub = sandbox.stub(videoUnitService, 'create').returns(of(response));
        const navigateSpy = sinon.spy(router, 'navigate');

        createVideoUnitComponentFixture.detectChanges();
        tick();
        const videoUnitForm: VideoUnitFormStubComponent = createVideoUnitComponentFixture.debugElement.query(By.directive(VideoUnitFormStubComponent)).componentInstance;
        videoUnitForm.formSubmitted.emit(formDate);

        createVideoUnitComponentFixture.whenStable().then(() => {
            const videoUnitCallArgument: VideoUnit = createStub.getCall(0).args[0];
            const lectureIdCallArgument: number = createStub.getCall(0).args[1];

            expect(videoUnitCallArgument.name).to.equal(formDate.name);
            expect(videoUnitCallArgument.description).to.equal(formDate.description);
            expect(videoUnitCallArgument.releaseDate).to.equal(formDate.releaseDate);
            expect(videoUnitCallArgument.source).to.equal(formDate.source);
            expect(lectureIdCallArgument).to.equal(1);

            expect(createStub).to.have.been.calledOnce;
            expect(navigateSpy).to.have.been.calledOnce;

            navigateSpy.restore();
        });
    }));
});
