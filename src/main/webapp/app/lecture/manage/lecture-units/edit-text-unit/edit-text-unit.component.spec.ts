import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { TextUnitApi } from 'app/openapi/api/text-unit-api';
import { MockProvider } from 'ng-mocks';
import { EditTextUnitComponent } from 'app/lecture/manage/lecture-units/edit-text-unit/edit-text-unit.component';
import { provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Alert, AlertService } from 'app/foundation/service/alert.service';
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

    beforeEach(async () => {
        global.ResizeObserver = MockResizeObserver as any;

        await TestBed.configureTestingModule({
            imports: [],
            providers: [
                MockProvider(TextUnitApi),
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
        vi.spyOn(alertService, 'error').mockReturnValue({ message: '' } as Alert);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });
    it('should initialize', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(editTextUnitComponent).not.toBeNull();
    });

    it('should set form data correctly', async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        const textUnitApi = TestBed.inject(TextUnitApi);

        const releaseDate = dayjs().year(2010).month(3).date(5);
        const originalTextUnit = { id: 1, name: 'Test', releaseDate: releaseDate.toISOString(), content: 'Lorem Ipsum', type: 'text' };

        const findByIdStub = vi.spyOn(textUnitApi, 'getTextUnit').mockReturnValue(of(originalTextUnit));
        fixture.detectChanges();
        const textUnitFormComponent: TextUnitFormComponent = fixture.debugElement.query(By.directive(TextUnitFormComponent)).componentInstance;

        fixture.detectChanges();
        await fixture.whenStable();
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editTextUnitComponent.formData().name).toEqual(originalTextUnit.name);
        expect(editTextUnitComponent.formData().releaseDate).toEqual(releaseDate);
        expect(editTextUnitComponent.formData().content).toEqual(originalTextUnit.content);
        expect(textUnitFormComponent.formData()).toEqual(editTextUnitComponent.formData());
    });

    it('should send PUT request upon form submission and navigate', async () => {
        const router: Router = TestBed.inject(Router);
        const textUnitApi = TestBed.inject(TextUnitApi);

        const originalTextUnit = { id: 1, name: 'Test', releaseDate: dayjs().year(2010).month(3).date(5).toISOString(), content: 'Lorem Ipsum', type: 'text' };

        const findByIdStub = vi.spyOn(textUnitApi, 'getTextUnit').mockReturnValue(of(originalTextUnit));

        const formDate: TextUnitFormData = {
            name: 'CHANGED',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };
        const updatedStub = vi.spyOn(textUnitApi, 'updateTextUnit').mockReturnValue(of({ ...originalTextUnit, name: formDate.name }));
        const navigateSpy = vi.spyOn(router, 'navigate');

        fixture.detectChanges();

        const textUnitForm: TextUnitFormComponent = fixture.debugElement.query(By.directive(TextUnitFormComponent)).componentInstance;
        textUnitForm.formSubmitted.emit(formDate);

        await fixture.whenStable();
        expect(findByIdStub).toHaveBeenCalledTimes(1);
        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ id: 1, name: 'CHANGED', content: 'Lorem Ipsum', releaseDate: formDate.releaseDate!.toISOString() }),
        );
        expect(navigateSpy).toHaveBeenCalledTimes(1);
        navigateSpy.mockRestore();
    });
});
