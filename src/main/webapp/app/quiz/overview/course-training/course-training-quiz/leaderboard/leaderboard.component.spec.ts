import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LeaderboardComponent } from './leaderboard.component';
import { LeaderboardEntry } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { MockTranslateService } from '../../../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';

describe('Leaderboard', () => {
    let component: LeaderboardComponent;
    let fixture: ComponentFixture<LeaderboardComponent>;
    let mockLeaderboardEntries: LeaderboardEntry[];

    const currentUserId = 1;

    beforeEach(async () => {
        mockLeaderboardEntries = [
            {
                rank: 1,
                selectedLeague: 1,
                userName: 'user1',
                userId: 1,
                score: 100,
                answeredCorrectly: 10,
                answeredWrong: 2,
                totalQuestions: 12,
                dueDate: '2023-06-15',
                streak: 3,
                imageURL: 'user1.jpg',
            },
            {
                rank: 2,
                selectedLeague: 1,
                userName: 'user2',
                userId: 2,
                score: 90,
                answeredCorrectly: 9,
                answeredWrong: 3,
                totalQuestions: 12,
                dueDate: '2023-06-15',
                streak: 2,
            },
        ];

        await TestBed.configureTestingModule({
            imports: [LeaderboardComponent, FontAwesomeModule, MockComponent(ProfilePictureComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(LeaderboardComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('currentUserId', currentUserId);
        fixture.componentRef.setInput('leaderboard', mockLeaderboardEntries);
        fixture.componentRef.setInput('leaderboardName', 'Test Name');

        fixture.detectChanges();
    });

    describe('isUserInLeaderboard', () => {
        it('should return true when current user is in leaderboard', () => {
            expect(component.isUserInLeaderboard()).toBeTrue();
        });

        it('should return false when current user is not in leaderboard', () => {
            fixture.componentRef.setInput('currentUserId', 999);
            fixture.detectChanges();
            expect(component.isUserInLeaderboard()).toBeFalse();
        });

        it('should return false when leaderboard is empty', () => {
            fixture.componentRef.setInput('leaderboard', []);
            fixture.detectChanges();
            expect(component.isUserInLeaderboard()).toBeFalse();
        });
    });

    describe('currentUserRank', () => {
        it('should return the rank of the current user', () => {
            expect(component.currentUserRank()).toBe(1);
        });

        it('should return 0 if user is not in leaderboard', () => {
            fixture.componentRef.setInput('currentUserId', 999);
            fixture.detectChanges();
            expect(component.currentUserRank()).toBe(0);
        });
    });

    describe('currentUserScore', () => {
        it('should return the score of the current user', () => {
            expect(component.currentUserScore()).toBe(100);
        });

        it('should return 0 if user is not in leaderboard', () => {
            fixture.componentRef.setInput('currentUserId', 999);
            fixture.detectChanges();
            expect(component.currentUserScore()).toBe(0);
        });
    });

    describe('currentUserPictureUrl', () => {
        it('should return the image URL of the current user', () => {
            expect(component.currentUserPictureUrl()).toBe('user1.jpg');
        });

        it('should return empty string if user is not in leaderboard', () => {
            fixture.componentRef.setInput('currentUserId', 999);
            fixture.detectChanges();
            expect(component.currentUserPictureUrl()).toBe('');
        });
    });
});
