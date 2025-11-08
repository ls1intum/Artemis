import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { QuizTrainingSettingsComponent } from './quiz-training-settings.component';
import { QuizTrainingSettingsService } from 'app/core/user/settings/quiz-training-settings/quiz-training-settings.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import * as globalUtils from 'app/shared/util/global.utils';
import { HttpResponse } from '@angular/common/http';
import { LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

describe('QuizTrainingSettingsComponent', () => {
    let component: QuizTrainingSettingsComponent;
    let fixture: ComponentFixture<QuizTrainingSettingsComponent>;
    let alertService: AlertService;

    const mockService = {
        getSettings: jest.fn(),
        updateSettings: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, QuizTrainingSettingsComponent],
            declarations: [MockDirective(TranslateDirective), MockComponent(HelpIconComponent)],
            providers: [{ provide: QuizTrainingSettingsService, useValue: mockService }, MockProvider(AlertService)],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizTrainingSettingsComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    const mockSettingsResponse = {
        showInLeaderboard: true,
    };

    it('should load leaderboard settings on init', fakeAsync(() => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));

        component.ngOnInit();
        tick();

        expect(mockService.getSettings).toHaveBeenCalled();
        expect(component.isVisibleInLeaderboard).toBeTrue();
    }));

    it('should update leaderboard settings when toggled', fakeAsync(() => {
        mockService.updateSettings.mockReturnValue(of({}));
        component.isVisibleInLeaderboard = true;

        jest.spyOn(alertService, 'success');

        component.toggleLeaderboardVisibility();
        tick();

        const expectedDto = new LeaderboardSettingsDTO();
        expectedDto.showInLeaderboard = true;

        expect(mockService.updateSettings).toHaveBeenCalledWith(expectedDto);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.quizTrainingSettings.updateSuccess');
    }));

    it('should handle error when updating settings', fakeAsync(() => {
        const error = new Error('Update failed');
        jest.spyOn(globalUtils, 'onError');
        mockService.updateSettings.mockReturnValue(throwError(() => error));

        component.toggleLeaderboardVisibility();
        tick();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    }));

    it('should display toggle when settings are loaded', fakeAsync(() => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));
        expect(toggleElement).toBeTruthy();
        expect(component.isVisibleInLeaderboard).toBeTrue();
    }));

    it('should display info message when no settings are available', fakeAsync(() => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: null })));

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const infoMessage = fixture.debugElement.query(By.css('.alert-info'));
        expect(infoMessage).toBeTruthy();
        expect(component.isVisibleInLeaderboard).toBeUndefined();
    }));

    it('should call toggleLeaderboardVisibility when toggle is changed', fakeAsync(() => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));
        mockService.updateSettings.mockReturnValue(of({}));

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const spy = jest.spyOn(component, 'toggleLeaderboardVisibility');
        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));

        toggleElement.nativeElement.click();
        tick();

        expect(spy).toHaveBeenCalled();
        expect(mockService.updateSettings).toHaveBeenCalled();
    }));

    it('should correctly reflect changes in isVisibleInLeaderboard', fakeAsync(() => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));
        mockService.updateSettings.mockReturnValue(of({}));

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));
        toggleElement.nativeElement.checked = false;
        toggleElement.nativeElement.dispatchEvent(new Event('change'));

        expect(component.isVisibleInLeaderboard).toBeFalse();
    }));
});
