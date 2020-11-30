import { HttpClient } from '@angular/common/http';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Achievement, AchievementRank } from 'app/entities/achievement.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-achievements',
    templateUrl: './achievements.component.html',
    styleUrls: ['./achievements.scss'],
})
export class AchievementsComponent implements OnInit, OnDestroy {
    @Input()
    public courseId: number;

    public achievements: Achievement[];

    readonly achievementRank = AchievementRank;

    private resourceUrl = SERVER_API_URL + 'api/courses';

    private subscription: Subscription;

    constructor(private http: HttpClient) {}

    ngOnInit() {
        this.subscription = this.http
            .get<Achievement[]>(`${this.resourceUrl}/${this.courseId}/earned-achievements`)
            .subscribe((loadedAchievements) => (this.achievements = loadedAchievements));
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }
}
