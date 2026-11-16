import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { firstValueFrom, of, throwError } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { QuizTrainingSettingsComponent } from './quiz-training-settings.component';
import { QuizTrainingApi } from 'app/openapi/api/quiz-training-api';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import * as globalUtils from 'app/foundation/util/global.utils';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuizTrainingSettingsComponent', () => {
    let component: QuizTrainingSettingsComponent;
    let fixture: ComponentFixture<QuizTrainingSettingsComponent>;
    let alertService: AlertService;

    const mockApi = {
        getLeaderboardSettings: vi.fn(),
        updateLeaderboardSettings: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, QuizTrainingSettingsComponent, MockDirective(TranslateDirective), MockComponent(HelpIconComponent)],
            providers: [{ provide: QuizTrainingApi, useValue: mockApi }, MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
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
        mockApi.getLeaderboardSettings.mockReturnValue(of(mockSettingsResponse));

        component.ngOnInit();
        await firstValueFrom(mockApi.getLeaderboardSettings());

        expect(mockApi.getLeaderboardSettings).toHaveBeenCalled();
        expect(component.isVisibleInLeaderboard()).toBe(true);
    });

    it('should update leaderboard settings when toggled', async () => {
        mockApi.updateLeaderboardSettings.mockReturnValue(of({}));
        vi.spyOn(alertService, 'success');

        component.onLeaderboardVisibilityChange(true);
        await firstValueFrom(mockApi.updateLeaderboardSettings());

        expect(mockApi.updateLeaderboardSettings).toHaveBeenCalledWith({ showInLeaderboard: true });
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.quizTrainingSettings.updateSuccess');
    });

    it('should handle error when updating settings', async () => {
        const error = new Error('Update failed');
        vi.spyOn(globalUtils, 'onError');
        mockApi.updateLeaderboardSettings.mockReturnValue(throwError(() => error));

        component.onLeaderboardVisibilityChange(true);
        await vi.waitFor(() => {
            expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
        });
    });

    it('should display toggle when settings are loaded', async () => {
        mockApi.getLeaderboardSettings.mockReturnValue(of(mockSettingsResponse));

        component.ngOnInit();
        await firstValueFrom(mockApi.getLeaderboardSettings());
        fixture.detectChanges();

        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));
        expect(toggleElement).toBeTruthy();
        expect(component.isVisibleInLeaderboard()).toBe(true);
    });

    it('should display info message when no settings are available', async () => {
        mockApi.getLeaderboardSettings.mockReturnValue(of({}));

        component.ngOnInit();
        await firstValueFrom(mockApi.getLeaderboardSettings());
        fixture.detectChanges();

        const infoMessage = fixture.debugElement.query(By.css('tumaet-ui-message'));
        expect(infoMessage).toBeTruthy();
        expect(component.isVisibleInLeaderboard()).toBeUndefined();
    });

    it('should save the new visibility when the toggle is changed', async () => {
        mockApi.getLeaderboardSettings.mockReturnValue(of(mockSettingsResponse));
        mockApi.updateLeaderboardSettings.mockReturnValue(of({}));

        component.ngOnInit();
        await firstValueFrom(mockApi.getLeaderboardSettings());
        fixture.detectChanges();

        // ngModel writes the initial value in a microtask, so let it settle before toggling it off again.
        await fixture.whenStable();
        fixture.detectChanges();

        const spy = vi.spyOn(component, 'onLeaderboardVisibilityChange');
        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));

        toggleElement.nativeElement.click();
        await firstValueFrom(mockApi.updateLeaderboardSettings());

        expect(spy).toHaveBeenCalledWith(false);
        expect(mockApi.updateLeaderboardSettings).toHaveBeenCalled();
    });

    it('should correctly reflect changes in isVisibleInLeaderboard', async () => {
        mockApi.getLeaderboardSettings.mockReturnValue(of(mockSettingsResponse));
        mockApi.updateLeaderboardSettings.mockReturnValue(of({}));

        component.ngOnInit();
        await firstValueFrom(mockApi.getLeaderboardSettings());
        fixture.detectChanges();

        const toggleElement = fixture.debugElement.query(By.css('#leaderboardVisibilityToggle'));
        toggleElement.nativeElement.checked = false;
        toggleElement.nativeElement.dispatchEvent(new Event('change'));

        expect(component.isVisibleInLeaderboard()).toBe(false);
    });
});
