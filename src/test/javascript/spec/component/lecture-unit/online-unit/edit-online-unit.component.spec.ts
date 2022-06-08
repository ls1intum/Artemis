import dayjs from 'dayjs/esm';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-online-unit/edit-online-unit.component';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-online-unit-form', template: '' })
class OnlineUnitFormStubComponent {
    @Input() isEditMode = false;
    @Input() formData: OnlineUnitFormData;
    @Output() formSubmitted: EventEmitter<OnlineUnitFormData> = new EventEmitter<OnlineUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('EditOnlineUnitComponent', () => {
    let editOnlineUnitComponentFixture: ComponentFixture<EditOnlineUnitComponent>;
    let editOnlineUnitComponent: EditOnlineUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [OnlineUnitFormStubComponent, LectureUnitLayoutStubComponent, EditOnlineUnitComponent],
            providers: [
                MockProvider(OnlineUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'onlineUnitId':
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
                editOnlineUnitComponentFixture = TestBed.createComponent(EditOnlineUnitComponent);
                editOnlineUnitComponent = editOnlineUnitComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editOnlineUnitComponentFixture.detectChanges();
        expect(editOnlineUnitComponent).not.toBeNull();
    });

    it('should set form data correctly', () => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const onlineUnitOfResponse = new OnlineUnit();
        onlineUnitOfResponse.id = 1;
        onlineUnitOfResponse.name = 'test';
        onlineUnitOfResponse.releaseDate = dayjs().year(2010).month(3).date(5);
        onlineUnitOfResponse.description = 'lorem ipsum';
        onlineUnitOfResponse.source = 'https://www.example.com';

        const response: HttpResponse<OnlineUnit> = new HttpResponse({
            body: onlineUnitOfResponse,
            status: 200,
        });

        const findByIdStub = jest.spyOn(onlineUnitService, 'findById').mockReturnValue(of(response));
        const onlineUnitFormStubComponent: OnlineUnitFormStubComponent = editOnlineUnitComponentFixture.debugElement.query(
            By.directive(OnlineUnitFormStubComponent),
        ).componentInstance;
        editOnlineUnitComponentFixture.detectChanges(); // onInit
        expect(editOnlineUnitComponent.onlineUnit).toEqual(onlineUnitOfResponse);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editOnlineUnitComponent.formData.name).toEqual(onlineUnitOfResponse.name);
        expect(editOnlineUnitComponent.formData.releaseDate).toEqual(onlineUnitOfResponse.releaseDate);
        expect(editOnlineUnitComponent.formData.description).toEqual(onlineUnitOfResponse.description);
        expect(editOnlineUnitComponent.formData.source).toEqual(onlineUnitOfResponse.source);
        expect(onlineUnitFormStubComponent.formData).toEqual(editOnlineUnitComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const onlineUnitInDatabase: OnlineUnit = new OnlineUnit();
        onlineUnitInDatabase.id = 1;
        onlineUnitInDatabase.name = 'test';
        onlineUnitInDatabase.releaseDate = dayjs().year(2010).month(3).date(5);
        onlineUnitInDatabase.description = 'lorem ipsum';
        onlineUnitInDatabase.source = 'https://www.example.com';

        const findByIdResponse: HttpResponse<OnlineUnit> = new HttpResponse({
            body: onlineUnitInDatabase,
            status: 200,
        });
        const findByIdStub = jest.spyOn(onlineUnitService, 'findById').mockReturnValue(of(findByIdResponse));

        editOnlineUnitComponentFixture.detectChanges(); // onInit
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editOnlineUnitComponent.onlineUnit).toEqual(onlineUnitInDatabase);

        const changedUnit: OnlineUnit = {
            ...onlineUnitInDatabase,
            name: 'Changed',
        };

        const updateResponse: HttpResponse<OnlineUnit> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedStub = jest.spyOn(onlineUnitService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const textUnitForm: OnlineUnitFormStubComponent = editOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormStubComponent)).componentInstance;
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
