import { TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { Injector } from '@angular/core';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ExerciseView, OrionBuildConnector, OrionExerciseConnector, OrionSharedUtilConnector, OrionState, OrionVCSConnector } from 'app/shared/orion/orion';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { AlertService } from 'app/core/util/alert.service';
import { REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { Feedback } from 'app/entities/feedback.model';

describe('OrionConnectorService', () => {
    let serviceUnderTest: OrionConnectorService;
    const router = new MockRouter();
    let alertService: AlertService;

    const exercise = { id: 42 } as ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                OrionConnectorService,
                Injector,
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useValue: router },
            ],
        });

        serviceUnderTest = TestBed.inject(OrionConnectorService);
        alertService = TestBed.inject(AlertService);

        // Mock all connectors. It may or may not be possible to do this automatically
        (window as any).orionSharedUtilConnector = { login: jest.fn(), log: jest.fn() } as OrionSharedUtilConnector;
        (window as any).orionExerciseConnector = {
            editExercise: jest.fn(),
            assessExercise: jest.fn(),
            downloadSubmission: jest.fn(),
            initializeAssessment: jest.fn(),
            importParticipation: jest.fn(),
        } as OrionExerciseConnector;
        (window as any).orionVCSConnector = { selectRepository: jest.fn(), submit: jest.fn() } as OrionVCSConnector;
        (window as any).orionBuildConnector = {
            buildAndTestLocally: jest.fn(),
            onBuildStarted: jest.fn(),
            onBuildFinished: jest.fn(),
            onBuildFailed: jest.fn(),
            onTestResult: jest.fn(),
        } as OrionBuildConnector;

        OrionConnectorService.initConnector(serviceUnderTest);
    });

    it('should return router', () => {
        expect(serviceUnderTest.router).toBe(router);
    });

    it('should forward login', () => {
        serviceUnderTest.login('name', 'pwd');

        expect((window as any).orionSharedUtilConnector.login).toHaveBeenCalledOnce();
        expect((window as any).orionSharedUtilConnector.login).toHaveBeenCalledWith('name', 'pwd');
    });

    it('should forward importParticipation', () => {
        serviceUnderTest.importParticipation('name', exercise);

        expect((window as any).orionExerciseConnector.importParticipation).toHaveBeenCalledOnce();
        expect((window as any).orionExerciseConnector.importParticipation).toHaveBeenCalledWith('name', '{"id":42}');
    });

    it('should forward submit', () => {
        serviceUnderTest.submit();

        expect((window as any).orionVCSConnector.submit).toHaveBeenCalledOnce();
        expect((window as any).orionVCSConnector.submit).toHaveBeenCalledWith();
    });

    it('should forward log', () => {
        serviceUnderTest.log('log string');

        expect((window as any).orionSharedUtilConnector.log).toHaveBeenCalledOnce();
        expect((window as any).orionSharedUtilConnector.log).toHaveBeenCalledWith('log string');
    });

    it('should forward onExerciseOpened', () => {
        let localState: OrionState = {} as any;
        serviceUnderTest.state().subscribe((state) => {
            localState = state;
        });

        serviceUnderTest.onExerciseOpened(5, 'TUTOR');

        expect(localState).toContainEntry(['view', ExerciseView.TUTOR]);
        expect(localState).toContainEntry(['opened', 5]);
    });

    it('should forward onBuildStarted', () => {
        serviceUnderTest.onBuildStarted('problem');

        expect((window as any).orionBuildConnector.onBuildStarted).toHaveBeenCalledOnce();
        expect((window as any).orionBuildConnector.onBuildStarted).toHaveBeenCalledWith('problem');
    });

    it('should forward onBuildFinished', () => {
        serviceUnderTest.onBuildFinished();

        expect((window as any).orionBuildConnector.onBuildFinished).toHaveBeenCalledOnce();
        expect((window as any).orionBuildConnector.onBuildFinished).toHaveBeenCalledWith();
    });

    it('should forward onBuildFailed with error', () => {
        serviceUnderTest.onBuildFailed([{ fileName: 'file', row: 5, column: 4, text: 'error', type: 'error', timestamp: 0 } as Annotation]);

        expect((window as any).orionBuildConnector.onBuildFailed).toHaveBeenCalledOnce();
        expect((window as any).orionBuildConnector.onBuildFailed).toHaveBeenCalledWith(
            '{"errors":{"file":[{"row":5,"column":4,"text":"error","type":"error","ts":0}]},"timestamp":0}',
        );
    });

    it('should forward onTestResult', () => {
        serviceUnderTest.onTestResult(true, 'name', 'message');

        expect((window as any).orionBuildConnector.onTestResult).toHaveBeenCalledOnce();
        expect((window as any).orionBuildConnector.onTestResult).toHaveBeenCalledWith(true, 'name', 'message');
    });

    it('should forward isBuilding', () => {
        let localState: OrionState = {} as any;
        serviceUnderTest.state().subscribe((state) => {
            localState = state;
        });

        serviceUnderTest.isBuilding(true);

        expect(localState).toContainEntry(['building', true]);
    });

    it('should forward isCloning', () => {
        let localState: OrionState = {} as any;
        serviceUnderTest.state().subscribe((state) => {
            localState = state;
        });

        serviceUnderTest.isCloning(true);

        expect(localState).toContainEntry(['cloning', true]);
    });

    it('should navigate on startedBuildInOrion', () => {
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');

        serviceUnderTest.startedBuildInOrion(5, 10);

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith('/courses/5/exercises/10?withIdeSubmit=true');
    });

    it('should throw error in updateAssessment if no component present', () => {
        const errorSpy = jest.spyOn(alertService, 'error');

        serviceUnderTest.updateAssessment(5, '');

        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.orion.assessment.updateFailed');
    });

    it('should forward in updateAssessment if component present', () => {
        serviceUnderTest.activeAssessmentComponent = { updateFeedback: jest.fn() } as any;

        serviceUnderTest.updateAssessment(10, '{"id":5}');

        expect(serviceUnderTest.activeAssessmentComponent!.updateFeedback).toHaveBeenCalledOnce();
        expect(serviceUnderTest.activeAssessmentComponent!.updateFeedback).toHaveBeenCalledWith(10, { id: 5 });
    });

    it('should forward editExercise', () => {
        let localState: OrionState = {} as any;
        serviceUnderTest.state().subscribe((state) => {
            localState = state;
        });

        serviceUnderTest.editExercise(exercise);

        expect(localState).toContainEntry(['cloning', true]);
        expect((window as any).orionExerciseConnector.editExercise).toHaveBeenCalledOnce();
        expect((window as any).orionExerciseConnector.editExercise).toHaveBeenCalledWith('{"id":42}');
    });

    it('should forward selectRepository', () => {
        serviceUnderTest.selectRepository(REPOSITORY.SOLUTION);

        expect((window as any).orionVCSConnector.selectRepository).toHaveBeenCalledOnce();
        expect((window as any).orionVCSConnector.selectRepository).toHaveBeenCalledWith(REPOSITORY.SOLUTION);
    });

    it('should forward buildAndTestLocally', () => {
        serviceUnderTest.buildAndTestLocally();

        expect((window as any).orionBuildConnector.buildAndTestLocally).toHaveBeenCalledOnce();
        expect((window as any).orionBuildConnector.buildAndTestLocally).toHaveBeenCalledWith();
    });

    it('should forward assessExercise', () => {
        let localState: OrionState = {} as any;
        serviceUnderTest.state().subscribe((state) => {
            localState = state;
        });

        serviceUnderTest.assessExercise(exercise);

        expect(localState).toContainEntry(['cloning', true]);
        expect((window as any).orionExerciseConnector.assessExercise).toHaveBeenCalledOnce();
        expect((window as any).orionExerciseConnector.assessExercise).toHaveBeenCalledWith('{"id":42}');
    });

    it('should forward downloadSubmission', () => {
        serviceUnderTest.downloadSubmission(5, 0, false, 'test');

        expect((window as any).orionExerciseConnector.downloadSubmission).toHaveBeenCalledOnce();
        expect((window as any).orionExerciseConnector.downloadSubmission).toHaveBeenCalledWith('5', '0', 'test');
    });

    it('should forward initializeAssessment', () => {
        const feedbacks = [{ id: 2, positive: false, detailText: 'abc' } as Feedback, { id: 3, positive: true, detailText: 'cde' } as Feedback];
        serviceUnderTest.initializeAssessment(5, feedbacks);

        expect((window as any).orionExerciseConnector.initializeAssessment).toHaveBeenCalledOnce();
        expect((window as any).orionExerciseConnector.initializeAssessment).toHaveBeenCalledWith(
            '5',
            '[{"id":2,"positive":false,"detailText":"abc"},{"id":3,"positive":true,"detailText":"cde"}]',
        );
    });
});
