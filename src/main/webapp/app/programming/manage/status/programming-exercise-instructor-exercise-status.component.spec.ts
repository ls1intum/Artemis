import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from './programming-exercise-instructor-exercise-status.component';

describe('ProgrammingExerciseInstructorExerciseStatusComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructorExerciseStatusComponent>;
    let templateResults: BehaviorSubject<Result | undefined>;
    let solutionResults: BehaviorSubject<Result | undefined>;

    beforeEach(() => {
        templateResults = new BehaviorSubject<Result | undefined>(undefined);
        solutionResults = new BehaviorSubject<Result | undefined>(undefined);
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseInstructorExerciseStatusComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ParticipationWebsocketService, {
                    subscribeForLatestResultOfParticipation: vi.fn((id: number) => (id === 1 ? templateResults : solutionResults)),
                }),
            ],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseInstructorExerciseStatusComponent);
    });

    it('does not show a successful status until both builds have known, expected scores', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('fa-icon')).toBeNull();
        fixture.componentRef.setInput('templateParticipation', { id: 1, submissions: [{ results: [{ id: 1, score: 0 }] }] });
        fixture.componentRef.setInput('solutionParticipation', { id: 2, submissions: [{ results: [{ id: 2, successful: true }] }] });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('fa-icon')).toBeNull();
        fixture.componentRef.setInput('solutionParticipation', { id: 2, submissions: [{ results: [{ id: 3, score: 100, successful: true }] }] });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="programming-build-success"]')).not.toBeNull();
    });

    it('accepts live results even when participations have no submissions yet', () => {
        fixture.componentRef.setInput('templateParticipation', { id: 1 });
        fixture.componentRef.setInput('solutionParticipation', { id: 2, submissions: [] });
        fixture.detectChanges();
        templateResults.next({ id: 3, score: 0 });
        solutionResults.next({ id: 4, score: 80, successful: true });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="programming-build-warning"]')).not.toBeNull();
        solutionResults.next({ id: 5, score: 100, successful: true });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="programming-build-success"]')).not.toBeNull();
    });

    it('clears warnings and unsubscribes when a participation is removed', () => {
        fixture.componentRef.setInput('solutionParticipation', { id: 2, submissions: [{ results: [{ id: 3, score: 80 }] }] });
        fixture.detectChanges();
        fixture.componentRef.setInput('solutionParticipation', undefined);
        fixture.detectChanges();
        solutionResults.next({ id: 4, score: 70 });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="programming-build-warning"]')).toBeNull();
        expect(fixture.componentInstance.solutionParticipationSubscription?.closed).toBe(true);
    });
});
