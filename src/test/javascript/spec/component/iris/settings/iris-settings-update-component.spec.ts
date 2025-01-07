import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { mockSettings, mockVariants } from './mock-settings';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { of } from 'rxjs';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMockDirective } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { MockJhiTranslateDirective } from '../../../helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('IrisSettingsUpdateComponent', () => {
    let component: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let getVariantsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                NgbTooltipMockDirective,
                MockJhiTranslateDirective,
                IrisCourseSettingsUpdateComponent,
                IrisSettingsUpdateComponent,
                IrisCommonSubSettingsUpdateComponent,
            ],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                MockProvider(IrisSettingsService, {
                    getGlobalSettings: () => of(mockSettings()),
                    getUncombinedCourseSettings: () => of(mockSettings()),
                    getUncombinedExerciseSettings: () => of(mockSettings()),
                }),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(IrisSettingsUpdateComponent);
                component = fixture.componentInstance;
                const irisSettingsService = TestBed.inject(IrisSettingsService);
                getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariantsForFeature').mockReturnValue(of(mockVariants()));
            });
    });

    it('should display the checkbox for lecture ingestion when settingsType is COURSE', fakeAsync(() => {
        component.irisSettings = mockSettings();
        component.settingsType = IrisSettingsType.COURSE;
        fixture.detectChanges();
        tick();
        fixture.detectChanges();
        const lectureIngestionElement = fixture.debugElement.query(By.css('jhi-iris-common-sub-settings-update'));
        const checkboxElement = fixture.debugElement.query(By.css('input[type="checkbox"]#autoLectureIngestion'));
        const labelElement = fixture.debugElement.query(By.css('label[for="autoLectureIngestion"]'));
        const globalSettingsElement = fixture.debugElement.query(By.css('jhi-iris-global-autoupdate-settings-update'));
        expect(globalSettingsElement).toBeFalsy();
        expect(lectureIngestionElement).not.toBeNull();
        expect(checkboxElement).toBeTruthy();
        expect(labelElement).toBeTruthy();
        expect(getVariantsSpy).toHaveBeenCalled();
    }));
});
