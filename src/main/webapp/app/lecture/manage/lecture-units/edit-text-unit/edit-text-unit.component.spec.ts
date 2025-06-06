import dayjs from 'dayjs/esm';

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/textUnit.service';
import { MockProvider } from 'ng-mocks';
import { EditTextUnitComponent } from 'app/lecture/manage/lecture-units/edit-text-unit/edit-text-unit.component';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Alert, AlertService } from 'app/shared/service/alert.service';
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

describe('EditTextUnitComponent', () => {
    let fixture: ComponentFixture<EditTextUnitComponent>;
    let editTextUnitComponent: EditTextUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TextUnitService),
                MockProvider(AlertService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
                        paramMap: of({
                            get: () => {
                                return { textUnitId: 1 };
                            },
                        }),
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: () => {
                                        return { lectureId: 1 };
                                    },
                                }),
                            },
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(EditTextUnitComponent);
        editTextUnitComponent = fixture.componentInstance;
        const alertService = TestBed.inject(AlertService);
        jest.spyOn(alertService, 'error').mockReturnValue({ message: '' } as Alert);
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        tick();
        expect(editTextUnitComponent).not.toBeNull();
    }));

    it('should set form data correctly', async () => {
        jest.spyOn(console, 'warn').mockImplementation(() => {});
        const textUnitService = TestBed.inject(TextUnitService);

        const originalTextUnit: TextUnit = new TextUnit();
        originalTextUnit.id = 1;
        originalTextUnit.name = 'Test';
        originalTextUnit.releaseDate = dayjs().year(2010).month(3).date(5);
        originalTextUnit.content = 'Lorem Ipsum';

        const response: HttpResponse<TextUnit> = new HttpResponse({
            body: originalTextUnit,
            status: 200,
        });

        const findByIdStub = jest.spyOn(textUnitService, 'findById').mockReturnValue(of(response));
        fixture.detectChanges();
        const textUnitFormComponent: TextUnitFormComponent = fixture.debugElement.query(By.directive(TextUnitFormComponent)).componentInstance;

        fixture.detectChanges();
        return fixture.whenStable().then(() => {
            expect(findByIdStub).toHaveBeenCalledOnce();
            expect(editTextUnitComponent.formData.name).toEqual(originalTextUnit.name);
            expect(editTextUnitComponent.formData.releaseDate).toEqual(originalTextUnit.releaseDate);
            expect(editTextUnitComponent.formData.content).toEqual(originalTextUnit.content);
            expect(textUnitFormComponent.formData()).toEqual(editTextUnitComponent.formData);
        });
    });

    it('should send PUT request upon form submission and navigate', async () => {
        const router: Router = TestBed.inject(Router);
        const textUnitService = TestBed.inject(TextUnitService);

        const originalTextUnit: TextUnit = new TextUnit();
        originalTextUnit.id = 1;
        originalTextUnit.name = 'Test';
        originalTextUnit.releaseDate = dayjs().year(2010).month(3).date(5);
        originalTextUnit.content = 'Lorem Ipsum';

        const findByidResponse: HttpResponse<TextUnit> = new HttpResponse({
            body: originalTextUnit,
            status: 200,
        });

        const findByIdStub = jest.spyOn(textUnitService, 'findById').mockReturnValue(of(findByidResponse));

        const formDate: TextUnitFormData = {
            name: 'CHANGED',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };
        const updatedTextUnit: TextUnit = new TextUnit();
        updatedTextUnit.id = 1;
        updatedTextUnit.name = formDate.name;
        updatedTextUnit.releaseDate = formDate.releaseDate;
        updatedTextUnit.content = formDate.content;

        const updateResponse: HttpResponse<TextUnit> = new HttpResponse({
            body: updatedTextUnit,
            status: 200,
        });
        const updatedStub = jest.spyOn(textUnitService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        fixture.detectChanges();

        const textUnitForm: TextUnitFormComponent = fixture.debugElement.query(By.directive(TextUnitFormComponent)).componentInstance;
        textUnitForm.formSubmitted.emit(formDate);

        return fixture.whenStable().then(() => {
            expect(findByIdStub).toHaveBeenCalledOnce();
            expect(updatedStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
            navigateSpy.mockRestore();
        });
    });
});
