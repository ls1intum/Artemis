import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { OnlineUnitApi } from 'app/openapi/api/online-unit-api';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditOnlineUnitComponent } from 'app/lecture/manage/lecture-units/edit-online-unit/edit-online-unit.component';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { OnlineUnitFormComponent } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from '../../../../core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('EditOnlineUnitComponent', () => {
    let editOnlineUnitComponentFixture: ComponentFixture<EditOnlineUnitComponent>;
    let editOnlineUnitComponent: EditOnlineUnitComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            providers: [
                MockProvider(OnlineUnitApi),
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
                                return null;
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
                                        return null;
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 2;
                                            }
                                            return null;
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
        }).compileComponents();

        editOnlineUnitComponentFixture = TestBed.createComponent(EditOnlineUnitComponent);
        editOnlineUnitComponent = editOnlineUnitComponentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        editOnlineUnitComponentFixture.detectChanges();
        expect(editOnlineUnitComponent).not.toBeNull();
    });

    it('should set form data correctly', () => {
        const onlineUnitApi = TestBed.inject(OnlineUnitApi);

        const releaseDate = dayjs().year(2010).month(3).date(5);
        const onlineUnitOfResponse = { id: 1, name: 'test', releaseDate: releaseDate.toISOString(), description: 'lorem ipsum', source: 'https://www.example.com', type: 'online' };

        const findByIdStub = vi.spyOn(onlineUnitApi, 'getOnlineUnit').mockReturnValue(of(onlineUnitOfResponse));
        editOnlineUnitComponentFixture.detectChanges();
        const onlineUnitFormComponent: OnlineUnitFormComponent = editOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormComponent)).componentInstance;
        editOnlineUnitComponentFixture.detectChanges(); // onInit
        expect(editOnlineUnitComponent.onlineUnit).toMatchObject({ id: 1, name: 'test', source: 'https://www.example.com' });
        expect(findByIdStub).toHaveBeenCalledWith(1, 1);
        expect(editOnlineUnitComponent.formData().name).toEqual(onlineUnitOfResponse.name);
        expect(editOnlineUnitComponent.formData().releaseDate).toEqual(releaseDate);
        expect(editOnlineUnitComponent.formData().description).toEqual(onlineUnitOfResponse.description);
        expect(editOnlineUnitComponent.formData().source).toEqual(onlineUnitOfResponse.source);
        expect(onlineUnitFormComponent.formData()).toEqual(editOnlineUnitComponent.formData());
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const onlineUnitApi = TestBed.inject(OnlineUnitApi);

        const releaseDate = dayjs().year(2010).month(3).date(5);
        const onlineUnitInDatabase = { id: 1, name: 'test', releaseDate: releaseDate.toISOString(), description: 'lorem ipsum', source: 'https://www.example.com', type: 'online' };
        const findByIdStub = vi.spyOn(onlineUnitApi, 'getOnlineUnit').mockReturnValue(of(onlineUnitInDatabase));

        editOnlineUnitComponentFixture.detectChanges(); // onInit
        expect(findByIdStub).toHaveBeenCalledTimes(1);
        expect(editOnlineUnitComponent.onlineUnit).toMatchObject({ id: 1, name: 'test' });

        const changedUnit = { ...onlineUnitInDatabase, name: 'Changed' };

        const updatedStub = vi.spyOn(onlineUnitApi, 'updateOnlineUnit').mockReturnValue(of(changedUnit));
        const navigateSpy = vi.spyOn(router, 'navigate');

        const textUnitForm: OnlineUnitFormComponent = editOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormComponent)).componentInstance;
        textUnitForm.formSubmitted.emit({
            name: changedUnit.name,
            description: changedUnit.description,
            releaseDate,
            source: changedUnit.source,
        });

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(1, expect.objectContaining({ id: 1, name: 'Changed', releaseDate: releaseDate.toISOString(), source: 'https://www.example.com' }));
        expect(navigateSpy).toHaveBeenCalledTimes(1);
        navigateSpy.mockRestore();
    });
});
