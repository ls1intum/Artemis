import 'zone.js';
import 'zone.js/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Subject, of, throwError } from 'rxjs';

import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { HttpClient } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { Location } from '@angular/common';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CodeEditorInstructorAndEditorContainerComponent - Code Generation', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    let http: jest.Mocked<Pick<HttpClient, 'post'>>;
    let ws: jest.Mocked<Pick<HyperionWebsocketService, 'subscribeToJob' | 'unsubscribeFromJob'>>;
    let alertService: AlertService;
    let profileService: ProfileService;

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch (error) {
            // already initialized in some runners
        }
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            providers: [
                { provide: AlertService, useClass: MockAlertService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: Router, useClass: MockRouter },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: DomainService, useValue: { setDomain: jest.fn() } },
                { provide: Location, useValue: { replaceState: jest.fn() } },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ActivatedRoute, useValue: { params: of({}) } },
                { provide: HttpClient, useValue: { post: jest.fn() } },
                { provide: NgbModal, useValue: { open: jest.fn(() => ({ componentInstance: {}, result: Promise.resolve() })) } },
                { provide: HyperionWebsocketService, useValue: { subscribeToJob: jest.fn(), unsubscribeFromJob: jest.fn() } },
                { provide: CodeEditorRepositoryService, useValue: { pull: jest.fn(() => of(void 0)) } },
                { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: jest.fn(() => of({} as any)) } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            // Avoid rendering heavy template dependencies for these tests
            .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
                set: { template: '', imports: [] as any },
            })
            .compileComponents();

        alertService = TestBed.inject(AlertService);
        http = TestBed.inject(HttpClient) as any;
        ws = TestBed.inject(HyperionWebsocketService) as any;
        profileService = TestBed.inject(ProfileService);

        // Enable Hyperion by default so property initialization is deterministic
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        // Minimal exercise setup used by generateCode
        comp.exercise = { id: 42 } as any;
    });

    afterEach(() => {
        fixture?.destroy();
        jest.clearAllMocks();
    });

    it('should not generate when no exercise id', async () => {
        comp.exercise = undefined as any;
        comp.selectedRepository = RepositoryType.TEMPLATE;

        comp.generateCode();
        await Promise.resolve();
        expect(http.post).not.toHaveBeenCalled();
    });

    it('should not generate when a generation is already running', async () => {
        comp.selectedRepository = RepositoryType.SOLUTION;
        comp.isGeneratingCode.set(true);

        comp.generateCode();
        await Promise.resolve();
        expect(http.post).not.toHaveBeenCalled();
    });

    it('should warn on unsupported repository types', () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.ASSIGNMENT; // unsupported

        comp.generateCode();

        expect(http.post).not.toHaveBeenCalled();
        expect(addAlertSpy).toHaveBeenCalledWith(
            expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' }),
        );
    });

    it('should call API, subscribe, and show success alert on DONE success', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TEMPLATE;

        (http.post as jest.Mock).mockReturnValue(of({ jobId: 'job-1' }));
        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

        comp.generateCode();
        await Promise.resolve(); // resolve modal

        expect(http.post).toHaveBeenCalledWith('api/hyperion/programming-exercises/42/generate-code', { repositoryType: 'TEMPLATE' });

        // Emit DONE success event
        job$.next({ type: 'DONE', success: true });

        expect(comp.isGeneratingCode()).toBeFalse();
        expect(addAlertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                type: AlertType.SUCCESS,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                translationParams: { repositoryType: RepositoryType.TEMPLATE },
            }),
        );
    });

    it('should call API, subscribe, and show partial success alert on DONE failure', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.SOLUTION;

        (http.post as jest.Mock).mockReturnValue(of({ jobId: 'job-2' }));
        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

        comp.generateCode();
        await Promise.resolve();

        expect(http.post).toHaveBeenCalledWith('api/hyperion/programming-exercises/42/generate-code', { repositoryType: 'SOLUTION' });

        job$.next({ type: 'DONE', success: false });

        expect(comp.isGeneratingCode()).toBeFalse();
        expect(addAlertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                type: AlertType.WARNING,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                translationParams: { repositoryType: RepositoryType.SOLUTION },
            }),
        );
    });

    it('should show error alert on API error', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TESTS;

        (http.post as jest.Mock).mockReturnValue(throwError(() => new Error('fail')) as any);

        comp.generateCode();
        await Promise.resolve();

        expect(http.post).toHaveBeenCalledWith('api/hyperion/programming-exercises/42/generate-code', { repositoryType: 'TESTS' });
        expect(comp.isGeneratingCode()).toBeFalse();
        expect(addAlertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                type: AlertType.DANGER,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
            }),
        );
    });

    it('should compute hyperionEnabled from profile service', async () => {
        const isModuleFeatureActiveSpy = jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        // Recreate the component to ensure the property is initialized using the spy value
        fixture.destroy();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_HYPERION);
        expect(comp.hyperionEnabled).toBeTrue();
    });
});
