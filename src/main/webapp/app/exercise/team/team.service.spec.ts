import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TeamService } from './team.service';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Course } from 'app/course/shared/entities/course.model';

describe('Team overview service', () => {
    let service: TeamService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(AccountService), MockProvider(WebsocketService)] });
        service = TestBed.inject(TeamService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it.each(['student', 'staff'] as const)('should request the explicit %s overview and preserve its payload', (purpose) => {
        let received: Course | undefined;
        service
            .findCourseWithExercisesAndParticipationsForTeam({ id: 7, isAtLeastInstructor: true }, { shortName: 'team' }, purpose)
            .subscribe((response) => (received = response.body ?? undefined));
        const request = http.expectOne((r) => r.method === 'GET');
        request.flush({ id: 7, exercises: [{ id: 8, studentParticipations: [{ id: 9, submissionCount: 2 }] }] });
        expect(request.request.url).toBe(`api/exercise/courses/7/teams/team/with-exercises-and-participations${purpose === 'staff' ? '/for-staff' : ''}`);
        expect(received?.exercises?.[0].studentParticipations?.[0].submissionCount).toBe(2);
    });
});
