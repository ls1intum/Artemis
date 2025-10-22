import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTrainingComponent } from './course-training.component';
import { ActivatedRoute, Router } from '@angular/router';
import { MockBuilder } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LeaderboardService } from './course-training-quiz/leaderboard/service/leaderboard-service';
import { LeaderboardDTO, LeaderboardEntry } from './course-training-quiz/leaderboard/leaderboard-types';
import { LocationStrategy, PathLocationStrategy } from '@angular/common';
import dayjs from 'dayjs/esm';

describe('CourseTrainingComponent', () => {
    let component: CourseTrainingComponent;
    let fixture: ComponentFixture<CourseTrainingComponent>;
    let leaderboardService: LeaderboardService;
    let consoleErrorSpy: jest.SpyInstance;

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

    beforeEach(async () => {
        consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();
        await MockBuilder(CourseTrainingComponent)
            .keep(Router)
            .provide([
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: LocationStrategy, useClass: PathLocationStrategy },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: 1 }),
                        },
                    },
                },
            ]);

        leaderboardService = TestBed.inject(LeaderboardService);
        jest.spyOn(leaderboardService, 'getQuizTrainingLeaderboard').mockReturnValue(of(mockLeaderboardDTO));

        fixture = TestBed.createComponent(CourseTrainingComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        consoleErrorSpy.mockRestore();
        jest.clearAllMocks();
    });

    it('should extract courseId from route params', () => {
        expect(component.courseId()).toBe(1);
    });

    it('should navigate to quiz', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToTraining();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'training', 'quiz']);
    });

    it('should load leaderboard data on initialization', () => {
        const leaderboardSpy = jest.spyOn(leaderboardService, 'getQuizTrainingLeaderboard');
        expect(leaderboardSpy).toHaveBeenCalledWith(1);
        expect(component.leaderboardEntries()).toEqual([mockLeaderboardEntry]);
        expect(component.currentUserEntry()).toEqual(mockLeaderboardEntry);
        expect(component.isLoading()).toBeFalse();
        expect(component.isDataLoaded()).toBeTrue();
        expect(component.showDialog).toBeFalse();
        expect(component.isFirstVisit()).toBeFalse();
    });

    it('should handle error when loading leaderboard data', () => {
        jest.clearAllMocks();
        jest.spyOn(leaderboardService, 'getQuizTrainingLeaderboard').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        component.loadLeaderboard(1);
        expect(component.isLoading()).toBeFalse();
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
        expect(dueIn.isValid).toBeTrue();
        expect(dueIn.isPast).toBeFalse();
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
        expect(dueIn.isValid).toBeTrue();
        expect(dueIn.isPast).toBeTrue();
    });

    it('should handle undefined due date', () => {
        component.currentTime.set(new Date(Date.now()).toISOString());
        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: undefined,
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBeFalse();
        expect(dueIn.isPast).toBeFalse();
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
        expect(dueIn.isValid).toBeTrue();
        expect(dueIn.isPast).toBeTrue();
        expect(dueIn.days).toBe(0);
        expect(dueIn.hours).toBe(0);
        expect(dueIn.minutes).toBe(0);
    });

    it('should save leaderboard settings', () => {
        const saveSpy = jest.spyOn(leaderboardService, 'initializeLeaderboardEntry').mockReturnValue(of(undefined));
        const loadSpy = jest.spyOn(component, 'loadLeaderboard');

        component.showInLeaderboard = true;
        component.isFirstVisit.set(true);
        component.onSaveDialog();

        expect(component.isFirstVisit()).toBeFalse();
        expect(saveSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                showInLeaderboard: true,
            }),
        );
        expect(loadSpy).toHaveBeenCalledWith(1);
    });

    it('should handle error when saving leaderboard settings', () => {
        jest.spyOn(leaderboardService, 'initializeLeaderboardEntry').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.isLoading.set(true);
        component.onSaveDialog();

        expect(component.isLoading()).toBeFalse();
    });
});
