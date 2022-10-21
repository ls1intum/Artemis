import { HttpResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { CreateOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-online-unit/create-online-unit.component';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';

@Component({ selector: 'jhi-online-unit-form', template: '' })
class OnlineUnitFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<OnlineUnitFormData> = new EventEmitter<OnlineUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('CreateOnlineUnitComponent', () => {
    let createOnlineUnitComponentFixture: ComponentFixture<CreateOnlineUnitComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [OnlineUnitFormStubComponent, LectureUnitLayoutStubComponent, CreateOnlineUnitComponent],
            providers: [
                MockProvider(OnlineUnitService),
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
                createOnlineUnitComponentFixture = TestBed.createComponent(CreateOnlineUnitComponent);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send POST request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formDate: OnlineUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.example.com',
        };

        const response: HttpResponse<OnlineUnit> = new HttpResponse({
            body: new OnlineUnit(),
            status: 201,
        });

        const createStub = jest.spyOn(onlineUnitService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createOnlineUnitComponentFixture.detectChanges();
        tick();
        const onlineUnitForm: OnlineUnitFormStubComponent = createOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormStubComponent)).componentInstance;
        onlineUnitForm.formSubmitted.emit(formDate);

        createOnlineUnitComponentFixture.whenStable().then(() => {
            const onlineUnitCallArgument: OnlineUnit = createStub.mock.calls[0][0];
            const lectureIdCallArgument: number = createStub.mock.calls[0][1];

            expect(onlineUnitCallArgument.name).toEqual(formDate.name);
            expect(onlineUnitCallArgument.description).toEqual(formDate.description);
            expect(onlineUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
            expect(onlineUnitCallArgument.source).toEqual(formDate.source);
            expect(lectureIdCallArgument).toBe(1);

            expect(createStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();

            navigateSpy.mockRestore();
        });
    }));
});
