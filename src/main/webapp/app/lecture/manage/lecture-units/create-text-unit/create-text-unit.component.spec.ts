import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CreateTextUnitComponent } from 'app/lecture/manage/lecture-units/create-text-unit/create-text-unit.component';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/text-unit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { ProfileService } from '../../../../core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('CreateTextUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let createTextUnitComponentFixture: ComponentFixture<CreateTextUnitComponent>;
    let createTextUnitComponent: CreateTextUnitComponent;

    beforeEach(async () => {
        global.ResizeObserver = MockResizeObserver as any;

        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TextUnitService),
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
                { provide: AccountService, useClass: MockAccountService },
                { provide: ThemeService, useClass: MockThemeService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        createTextUnitComponentFixture = TestBed.createComponent(CreateTextUnitComponent);
        createTextUnitComponent = createTextUnitComponentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', async () => {
        createTextUnitComponentFixture.detectChanges();
        await createTextUnitComponentFixture.whenStable();
        expect(createTextUnitComponent).not.toBeNull();
    });

    it('should send POST request upon form submission and navigate', async () => {
        const router: Router = TestBed.inject(Router);
        const textUnitService = TestBed.inject(TextUnitService);
        const formDate: TextUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        const persistedTextUnit: TextUnit = new TextUnit();
        persistedTextUnit.id = 1;
        persistedTextUnit.name = formDate.name;
        persistedTextUnit.releaseDate = formDate.releaseDate;
        persistedTextUnit.content = formDate.content;

        const response: HttpResponse<TextUnit> = new HttpResponse({
            body: persistedTextUnit,
            status: 200,
        });

        const createStub = vi.spyOn(textUnitService, 'create').mockReturnValue(of(response));
        const navigateSpy = vi.spyOn(router, 'navigate');

        createTextUnitComponentFixture.detectChanges();
        await createTextUnitComponentFixture.whenStable();

        const textUnitForm: TextUnitFormComponent = createTextUnitComponentFixture.debugElement.query(By.directive(TextUnitFormComponent)).componentInstance;
        textUnitForm.formSubmitted.emit(formDate);

        await createTextUnitComponentFixture.whenStable();
        expect(createStub).toHaveBeenCalledTimes(1);
        expect(navigateSpy).toHaveBeenCalledTimes(1);
        navigateSpy.mockRestore();
    });
});
