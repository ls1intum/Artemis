import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { firstValueFrom, of, throwError } from 'rxjs';
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
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuizTrainingSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: QuizTrainingSettingsComponent;
    let fixture: ComponentFixture<QuizTrainingSettingsComponent>;
    let alertService: AlertService;

    const mockService = {
        getSettings: vi.fn(),
        updateSettings: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, QuizTrainingSettingsComponent, MockDirective(TranslateDirective), MockComponent(HelpIconComponent)],
            providers: [{ provide: QuizTrainingSettingsService, useValue: mockService }, MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizTrainingSettingsComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const mockSettingsResponse = {
        showInLeaderboard: true,
    };

    it('should load leaderboard settings on init', async () => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));

        component.ngOnInit();
        await firstValueFrom(mockService.getSettings());

        expect(mockService.getSettings).toHaveBeenCalled();
        expect(component.isVisibleInLeaderboard).toBe(true);
    });

    it('should update leaderboard settings when toggled', async () => {
        mockService.updateSettings.mockReturnValue(of({}));
        component.isVisibleInLeaderboard = true;

        vi.spyOn(alertService, 'success');

        component.toggleLeaderboardVisibility();
        await firstValueFrom(mockService.updateSettings());

        const expectedDto = new LeaderboardSettingsDTO();
        expectedDto.showInLeaderboard = true;

        expect(mockService.updateSettings).toHaveBeenCalledWith(expectedDto);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.quizTrainingSettings.updateSuccess');
    });

    it('should handle error when updating settings', async () => {
        const error = new Error('Update failed');
        vi.spyOn(globalUtils, 'onError');
        mockService.updateSettings.mockReturnValue(throwError(() => error));

        component.toggleLeaderboardVisibility();
        await vi.waitFor(() => {
            expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
        });
    });

    it('should display toggle when settings are loaded', async () => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));

        component.ngOnInit();
        await firstValueFrom(mockService.getSettings());
        fixture.detectChanges();

        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));
        expect(toggleElement).toBeTruthy();
        expect(component.isVisibleInLeaderboard).toBe(true);
    });

    it('should display info message when no settings are available', async () => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: null })));

        component.ngOnInit();
        await firstValueFrom(mockService.getSettings());
        fixture.detectChanges();

        const infoMessage = fixture.debugElement.query(By.css('.alert-info'));
        expect(infoMessage).toBeTruthy();
        expect(component.isVisibleInLeaderboard).toBeUndefined();
    });

    it('should call toggleLeaderboardVisibility when toggle is changed', async () => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));
        mockService.updateSettings.mockReturnValue(of({}));

        component.ngOnInit();
        await firstValueFrom(mockService.getSettings());
        fixture.detectChanges();

        const spy = vi.spyOn(component, 'toggleLeaderboardVisibility');
        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));

        toggleElement.nativeElement.click();
        await firstValueFrom(mockService.updateSettings());

        expect(spy).toHaveBeenCalled();
        expect(mockService.updateSettings).toHaveBeenCalled();
    });

    it('should correctly reflect changes in isVisibleInLeaderboard', async () => {
        mockService.getSettings.mockReturnValue(of(new HttpResponse({ body: mockSettingsResponse })));
        mockService.updateSettings.mockReturnValue(of({}));

        component.ngOnInit();
        await firstValueFrom(mockService.getSettings());
        fixture.detectChanges();

        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));
        toggleElement.nativeElement.checked = false;
        toggleElement.nativeElement.dispatchEvent(new Event('change'));

        expect(component.isVisibleInLeaderboard).toBe(false);
    });
});
