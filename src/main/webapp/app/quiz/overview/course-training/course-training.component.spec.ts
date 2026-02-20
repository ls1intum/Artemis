import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTrainingComponent } from './course-training.component';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LeaderboardService } from './course-training-quiz/leaderboard/service/leaderboard-service';
import { LeaderboardDTO, LeaderboardEntry, LeaderboardSettingsDTO } from './course-training-quiz/leaderboard/leaderboard-types';
import { LocationStrategy, PathLocationStrategy } from '@angular/common';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CourseTrainingComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CourseTrainingComponent;
    let fixture: ComponentFixture<CourseTrainingComponent>;
    let leaderboardService: LeaderboardService;

    const mockLeaderboardEntry: LeaderboardEntry = {
        rank: 1,
        selectedLeague: 3,
        userName: 'TestUser',
        userId: 1,
        score: 250,
        answeredCorrectly: 25,
        answeredWrong: 5,
        totalQuestions: 30,
        dueDate: new Date(Date.now()).toISOString(),
        streak: 5,
    };

    const mockLeaderboardDTO: LeaderboardDTO = {
        leaderboardEntries: [mockLeaderboardEntry],
        hasUserSetSettings: true,
        currentUserEntry: mockLeaderboardEntry,
        currentTime: new Date(Date.now()).toISOString(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseTrainingComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                Router,
                LeaderboardService,
                { provide: LocationStrategy, useClass: PathLocationStrategy },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: 1 }),
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                leaderboardService = TestBed.inject(LeaderboardService);
                vi.spyOn(leaderboardService, 'getQuizTrainingLeaderboard').mockResolvedValue(mockLeaderboardDTO);
                vi.spyOn(leaderboardService, 'getSettings').mockResolvedValue({ showInLeaderboard: true } as LeaderboardSettingsDTO);

                fixture = TestBed.createComponent(CourseTrainingComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should extract courseId from route params', () => {
        expect(component.courseId()).toBe(1);
    });

    it('should navigate to quiz', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');
        component.navigateToTraining();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'training', 'quiz']);
    });

    it('should load leaderboard data on initialization', async () => {
        const leaderboardSpy = vi.spyOn(leaderboardService, 'getQuizTrainingLeaderboard').mockResolvedValue(mockLeaderboardDTO);

        await component.loadLeaderboard(1);

        expect(leaderboardSpy).toHaveBeenCalledWith(1);
        expect(component.leaderboardEntries()).toEqual([mockLeaderboardEntry]);
        expect(component.currentUserEntry()).toEqual(mockLeaderboardEntry);
        expect(component.isLoading()).toBe(false);
        expect(component.isDataLoaded()).toBe(true);
        expect(component.showDialog).toBe(false);
        expect(component.isFirstVisit()).toBe(false);
    });

    it('should calculate league based on user entry', () => {
        component.currentUserEntry.set({ ...mockLeaderboardEntry, selectedLeague: 1 });
        expect(component.league()).toBe('Master');

        component.currentUserEntry.set({ ...mockLeaderboardEntry, selectedLeague: 2 });
        expect(component.league()).toBe('Diamond');

        component.currentUserEntry.set({ ...mockLeaderboardEntry, selectedLeague: 3 });
        expect(component.league()).toBe('Gold');

        component.currentUserEntry.set({ ...mockLeaderboardEntry, selectedLeague: 4 });
        expect(component.league()).toBe('Silver');

        component.currentUserEntry.set({ ...mockLeaderboardEntry, selectedLeague: 5 });
        expect(component.league()).toBe('Bronze');

        component.currentUserEntry.set({ ...mockLeaderboardEntry, selectedLeague: 0 });
        expect(component.league()).toBe('No League');
    });

    it('should calculate due date information correctly', () => {
        component.currentTime.set(new Date(Date.now()).toISOString());
        const futureDateString = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000 + 3 * 60 * 60 * 1000).toISOString();

        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: futureDateString,
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBe(true);
        expect(dueIn.isPast).toBe(false);
        expect(dueIn.days).toBe(2);
        expect(dueIn.hours).toBe(3);
    });

    it('should handle expired due dates', () => {
        component.currentTime.set(new Date(Date.now()).toISOString());
        const pastDateString = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();

        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: pastDateString,
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBe(true);
        expect(dueIn.isPast).toBe(true);
    });

    it('should handle undefined due date', () => {
        component.currentTime.set(new Date(Date.now()).toISOString());
        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: undefined,
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBe(false);
        expect(dueIn.isPast).toBe(false);
        expect(dueIn.days).toBe(0);
        expect(dueIn.hours).toBe(0);
        expect(dueIn.minutes).toBe(0);
    });

    it('should handle due date less than 60 seconds in future', () => {
        component.currentTime.set(new Date(Date.now()).toISOString());
        const almostDueDate = dayjs(component.currentTime()).add(30, 'second').toISOString();

        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: almostDueDate,
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBe(true);
        expect(dueIn.isPast).toBe(true);
        expect(dueIn.days).toBe(0);
        expect(dueIn.hours).toBe(0);
        expect(dueIn.minutes).toBe(0);
    });

    it('should return defaults when currentUserEntry is undefined', () => {
        component.currentUserEntry.set(undefined);

        expect(component.totalQuestions()).toBe(0);
        expect(component.correctQuestions()).toBe(0);
        expect(component.wrongQuestions()).toBe(0);
        expect(component.leaderboardName()).toBe('');
        expect(component.points()).toBe(0);
    });

    it('should return values from currentUserEntry when present', () => {
        component.currentUserEntry.set(mockLeaderboardEntry);

        expect(component.totalQuestions()).toBe(mockLeaderboardEntry.totalQuestions);
        expect(component.correctQuestions()).toBe(mockLeaderboardEntry.answeredCorrectly);
        expect(component.wrongQuestions()).toBe(mockLeaderboardEntry.answeredWrong);
        expect(component.leaderboardName()).toBe(mockLeaderboardEntry.userName);
        expect(component.points()).toBe(mockLeaderboardEntry.score);
    });

    it('should save leaderboard settings', async () => {
        const saveSpy = vi.spyOn(leaderboardService, 'updateSettings').mockResolvedValue(undefined);
        const loadSpy = vi.spyOn(component, 'loadLeaderboard').mockResolvedValue(undefined);

        component.showInLeaderboard = true;
        component.isFirstVisit.set(true);
        await component.onSaveDialog();

        expect(component.isFirstVisit()).toBe(false);
        expect(saveSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                showInLeaderboard: true,
            }),
        );
        expect(loadSpy).toHaveBeenCalledWith(1);
    });

    it('should handle error when saving leaderboard settings', async () => {
        vi.spyOn(leaderboardService, 'updateSettings').mockRejectedValue(new HttpErrorResponse({ status: 500 }));

        component.isLoading.set(true);
        await component.onSaveDialog();

        expect(component.isLoading()).toBe(false);
    });

    it('should load settings and open info dialog', async () => {
        const getSettingsSpy = vi.spyOn(leaderboardService, 'getSettings').mockResolvedValue({ showInLeaderboard: true });

        await component.showInfoDialog();

        expect(component.isLoading()).toBe(false);
        expect(getSettingsSpy).toHaveBeenCalled();
        expect(component.showInLeaderboard).toBe(true);
        expect(component.initialShowInLeaderboard()).toBe(true);
        expect(component.displayInfoDialog).toBe(true);
    });

    it('should handle error when loading settings for info dialog', async () => {
        vi.spyOn(leaderboardService, 'getSettings').mockRejectedValue(new HttpErrorResponse({ status: 500 }));

        component.displayInfoDialog = false;
        component.isLoading.set(true);
        await component.showInfoDialog();

        expect(component.isLoading()).toBe(false);
        expect(component.displayInfoDialog).toBe(false);
    });

    it('should save info dialog settings and reload leaderboard', async () => {
        const updateSettingsSpy = vi.spyOn(leaderboardService, 'updateSettings').mockResolvedValue(undefined);
        const loadLeaderboardSpy = vi.spyOn(component, 'loadLeaderboard').mockResolvedValue(undefined);

        component.showInLeaderboard = false;
        component.displayInfoDialog = true;
        await component.onSaveInfoDialog();

        expect(updateSettingsSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                showInLeaderboard: false,
            }),
        );
        expect(component.initialShowInLeaderboard()).toBe(false);
        expect(component.displayInfoDialog).toBe(false);
        expect(loadLeaderboardSpy).toHaveBeenCalledWith(1);
    });

    it('should handle error when saving info dialog settings', async () => {
        vi.spyOn(leaderboardService, 'updateSettings').mockRejectedValue(new HttpErrorResponse({ status: 500 }));

        component.displayInfoDialog = true;
        component.isLoading.set(true);
        await component.onSaveInfoDialog();

        expect(component.isLoading()).toBe(false);
        expect(component.displayInfoDialog).toBe(true);
    });
});
