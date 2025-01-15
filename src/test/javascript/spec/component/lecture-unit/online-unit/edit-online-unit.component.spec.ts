import dayjs from 'dayjs/esm';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-online-unit/edit-online-unit.component';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../../test.module';
import { OnlineUnitFormComponent } from '../../../../../../main/webapp/app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

describe('EditOnlineUnitComponent', () => {
    let editOnlineUnitComponentFixture: ComponentFixture<EditOnlineUnitComponent>;
    let editOnlineUnitComponent: EditOnlineUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, OwlNativeDateTimeModule],
            providers: [
                MockProvider(OnlineUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
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
        editOnlineUnitComponentFixture.detectChanges();
        const onlineUnitFormComponent: OnlineUnitFormComponent = editOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormComponent)).componentInstance;
        editOnlineUnitComponentFixture.detectChanges(); // onInit
        expect(editOnlineUnitComponent.onlineUnit).toEqual(onlineUnitOfResponse);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editOnlineUnitComponent.formData.name).toEqual(onlineUnitOfResponse.name);
        expect(editOnlineUnitComponent.formData.releaseDate).toEqual(onlineUnitOfResponse.releaseDate);
        expect(editOnlineUnitComponent.formData.description).toEqual(onlineUnitOfResponse.description);
        expect(editOnlineUnitComponent.formData.source).toEqual(onlineUnitOfResponse.source);
        expect(onlineUnitFormComponent.formData()).toEqual(editOnlineUnitComponent.formData);
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

        const textUnitForm: OnlineUnitFormComponent = editOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormComponent)).componentInstance;
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
