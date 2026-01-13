import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CreateOnlineUnitComponent } from 'app/lecture/manage/lecture-units/create-online-unit/create-online-unit.component';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
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

describe('CreateOnlineUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let createOnlineUnitComponentFixture: ComponentFixture<CreateOnlineUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(OnlineUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
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
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        createOnlineUnitComponentFixture = TestBed.createComponent(CreateOnlineUnitComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should send POST request upon form submission and navigate', async () => {
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

        const createStub = vi.spyOn(onlineUnitService, 'create').mockReturnValue(of(response));
        const navigateSpy = vi.spyOn(router, 'navigate');

        createOnlineUnitComponentFixture.detectChanges();
        await createOnlineUnitComponentFixture.whenStable();
        const onlineUnitForm: OnlineUnitFormComponent = createOnlineUnitComponentFixture.debugElement.query(By.directive(OnlineUnitFormComponent)).componentInstance;
        onlineUnitForm.formSubmitted.emit(formDate);

        await createOnlineUnitComponentFixture.whenStable();
        const onlineUnitCallArgument: OnlineUnit = createStub.mock.calls[0][0];
        const lectureIdCallArgument: number = createStub.mock.calls[0][1];

        expect(onlineUnitCallArgument.name).toEqual(formDate.name);
        expect(onlineUnitCallArgument.description).toEqual(formDate.description);
        expect(onlineUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
        expect(onlineUnitCallArgument.source).toEqual(formDate.source);
        expect(lectureIdCallArgument).toBe(1);

        expect(createStub).toHaveBeenCalledTimes(1);
        expect(navigateSpy).toHaveBeenCalledTimes(1);

        navigateSpy.mockRestore();
    });
});
