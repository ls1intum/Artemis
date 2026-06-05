import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal } from '@angular/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Subject } from 'rxjs';
import { By } from '@angular/platform-browser';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';

/**
 * Typed view onto the protected `latestResult` signal so the spec can read it without a blanket
 * `(component as any)` cast. The shape mirrors the component declaration.
 */
type InstructorStatusInternals = ProgrammingExerciseInstructorStatusComponent & {
    latestResult: Signal<Result | undefined>;
};
const internals = (c: ProgrammingExerciseInstructorStatusComponent): InstructorStatusInternals => c as InstructorStatusInternals;

describe('ProgrammingExerciseInstructorStatusComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseInstructorStatusComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorStatusComponent>;
    let participationWebsocketService: ParticipationWebsocketService;
    let subscribeForLatestResultStub: ReturnType<typeof vi.spyOn>;
    let latestResultSubject: Subject<Result>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseInstructorStatusComponent, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe)],
            providers: [LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseInstructorStatusComponent);
        comp = fixture.componentInstance;

        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);

        subscribeForLatestResultStub = vi.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
        latestResultSubject = new Subject();
        subscribeForLatestResultStub.mockReturnValue(latestResultSubject);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        latestResultSubject.complete();
        latestResultSubject = new Subject();
        subscribeForLatestResultStub.mockReturnValue(latestResultSubject);
    });

    it('should not show anything without inputs', () => {
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should not show anything if participationType is Assignment', () => {
        fixture.componentRef.setInput('participationType', ProgrammingExerciseParticipationType.ASSIGNMENT);
        fixture.componentRef.setInput('participation', { id: 1, results: [{ id: 1, successful: true, score: 100 } as Result] } as ProgrammingExerciseStudentParticipation);
        fixture.detectChanges();
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    [ProgrammingExerciseParticipationType.TEMPLATE, ProgrammingExerciseParticipationType.SOLUTION].map((participationType) =>
        it('should not show anything if there is no participation', () => {
            fixture.componentRef.setInput('participationType', participationType);
            fixture.detectChanges();
            const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
            expect(templateStatus).toBeNull();
            const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
            expect(solutionStatus).toBeNull();
        }),
    );

    it('should show nothing if the participation is template and the latest result has a score of 0', () => {
        const latestResult = { id: 3, successful: false, score: 0 } as Result;
        fixture.componentRef.setInput('participationType', ProgrammingExerciseParticipationType.TEMPLATE);
        fixture.componentRef.setInput('participation', {
            id: 1,
            submissions: [{ results: [latestResult, { id: 2, successful: false, score: 99 } as Result] }],
        } as TemplateProgrammingExerciseParticipation);
        fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
        fixture.detectChanges();

        expect(internals(comp).latestResult()).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should show nothing if the participation is solution and the latest result is successful', () => {
        const latestResult = { id: 3, successful: true, score: 100 } as Result;
        fixture.componentRef.setInput('participationType', ProgrammingExerciseParticipationType.SOLUTION);
        fixture.componentRef.setInput('participation', {
            id: 1,
            submissions: [{ results: [{ id: 2, successful: false, score: 99 } as Result, latestResult] }],
        } as SolutionProgrammingExerciseParticipation);
        fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
        fixture.detectChanges();

        expect(internals(comp).latestResult()).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should show a template warning if the participation is template and the score is > 0', () => {
        const latestResult = { id: 3, successful: false, score: 40 } as Result;
        fixture.componentRef.setInput('participationType', ProgrammingExerciseParticipationType.TEMPLATE);
        fixture.componentRef.setInput('participation', {
            id: 1,
            submissions: [{ results: [latestResult, { id: 2, successful: false, score: 99 } as Result] }],
        } as TemplateProgrammingExerciseParticipation);
        fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
        fixture.detectChanges();

        expect(internals(comp).latestResult()).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeDefined();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeNull();
    });

    it('should show a solution warning if the participation is solution and the result is not successful', () => {
        const latestResult = { id: 3, successful: false, score: 40 } as Result;
        fixture.componentRef.setInput('participationType', ProgrammingExerciseParticipationType.SOLUTION);
        fixture.componentRef.setInput('participation', {
            id: 1,
            submissions: [{ results: [{ id: 2, successful: false, score: 99 } as Result, latestResult] }],
        } as SolutionProgrammingExerciseParticipation);
        fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
        fixture.detectChanges();

        expect(internals(comp).latestResult()).toEqual(latestResult);
        const templateStatus = fixture.debugElement.query(By.css('#instructor-status-template'));
        expect(templateStatus).toBeNull();
        const solutionStatus = fixture.debugElement.query(By.css('#instructor-status-solution'));
        expect(solutionStatus).toBeDefined();
    });

    it('should update the latestResult on update from the result subscription', () => {
        const newResult = { id: 4, successful: true, score: 40 } as Result;
        const latestResult = { id: 3, successful: false, score: 40 } as Result;
        fixture.componentRef.setInput('participationType', ProgrammingExerciseParticipationType.TEMPLATE);
        fixture.componentRef.setInput('participation', {
            id: 1,
            results: [latestResult, { id: 2, successful: false, score: 99 } as Result],
        } as TemplateProgrammingExerciseParticipation);
        fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
        fixture.detectChanges();

        latestResultSubject.next(newResult);

        expect(internals(comp).latestResult()).toEqual(newResult);
    });
});
