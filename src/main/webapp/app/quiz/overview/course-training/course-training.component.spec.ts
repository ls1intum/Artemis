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
        dueDate: '2023-12-31T23:59:59Z',
        streak: 5,
    };

    const mockLeaderboardDTO: LeaderboardDTO = {
        leaderboardEntryDTO: [mockLeaderboardEntry],
        hasUserSetSettings: true,
        currentUserEntry: mockLeaderboardEntry,
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
        const futureDate = new Date();
        futureDate.setDate(futureDate.getDate() + 2);
        futureDate.setHours(futureDate.getHours() + 3);

        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: futureDate.toISOString(),
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBeTrue();
        expect(dueIn.isPast).toBeFalse();
        expect(dueIn.days).toBe(2);
    });

    it('should handle expired due dates', () => {
        const pastDate = new Date();
        pastDate.setDate(pastDate.getDate() - 1);

        component.currentUserEntry.set({
            ...mockLeaderboardEntry,
            dueDate: pastDate.toISOString(),
        });

        const dueIn = component.dueIn();
        expect(dueIn.isValid).toBeTrue();
        expect(dueIn.isPast).toBeTrue();
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

        component.isLoading.set(false);
        component.onSaveDialog();

        expect(component.isLoading()).toBeFalse();
    });
});
