import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ExportToCsv } from 'export-to-csv';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { PlagiarismCheckState, PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ArtemisTestModule } from '../../test.module';
import { downloadFile } from 'app/shared/util/download.util';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockProvider, MockDirective, MockComponent, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PlagiarismDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-details/plagiarism-details.component';
import { PlagiarismRunDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';

jest.mock('app/shared/util/download.util', () => ({
    downloadFile: jest.fn(),
}));

const generateCsv = jest.fn();

jest.mock('export-to-csv', () => ({
    ExportToCsv: jest.fn().mockImplementation(() => ({
        generateCsv,
    })),
}));

describe('Plagiarism Inspector Component', () => {
    let comp: PlagiarismInspectorComponent;
    let fixture: ComponentFixture<PlagiarismInspectorComponent>;
    let modelingExerciseService: ModelingExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let textExerciseService: TextExerciseService;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const programmingExercise = { id: 345, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
    const activatedRoute = {
        data: of({ exercise: modelingExercise }),
    };
    const comparisons = [
        {
            submissionA: { studentLogin: 'student1A' },
            submissionB: { studentLogin: 'student1B' },
            similarity: 0.5,
            status: PlagiarismStatus.NONE,
        },
        {
            submissionA: { studentLogin: 'student2A' },
            submissionB: { studentLogin: 'student2B' },
            similarity: 0.8,
            status: PlagiarismStatus.NONE,
        },
        {
            submissionA: { studentLogin: 'student3A' },
            submissionB: { studentLogin: 'student3B' },
            similarity: 0.7,
            status: PlagiarismStatus.NONE,
        },
    ];
    const modelingPlagiarismResult = {
        comparisons,
    } as ModelingPlagiarismResult;

    const textPlagiarismResult = {
        comparisons,
    } as TextPlagiarismResult;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                PlagiarismInspectorComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockDirective(NgModel),
                MockComponent(PlagiarismDetailsComponent),
                MockComponent(PlagiarismRunDetailsComponent),
                MockComponent(PlagiarismSidebarComponent),
            ],
            providers: [{ provide: ActivatedRoute, useValue: activatedRoute }, MockProvider(JhiWebsocketService), MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismInspectorComponent);
                comp = fixture.componentInstance;
                modelingExerciseService = TestBed.inject(ModelingExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
            });
    });

    it('should register to topic and fetch latest results on init', fakeAsync(() => {
        const websocketService = TestBed.inject(JhiWebsocketService);
        const websocketServiceSpy = jest.spyOn(websocketService, 'subscribe');
        jest.spyOn(websocketService, 'receive').mockReturnValue(of({ state: 'COMPLETED', messages: 'a message' } as PlagiarismCheckState));
        jest.spyOn(modelingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(modelingPlagiarismResult));

        comp.ngOnInit();
        tick();

        expect(websocketServiceSpy).toHaveBeenCalledWith(comp.getPlagarismDetectionTopic());
        expect(comp.getPlagarismDetectionTopic()).toEqual(`/topic/modeling-exercises/${modelingExercise.id}/plagiarism-check`);
        expect(comp.detectionInProgress).toBe(false);
        expect(comp.plagiarismResult).toBe(modelingPlagiarismResult);
    }));

    it('should return the correct topic url', () => {
        const exerciseTypes = [ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING];
        exerciseTypes.forEach((exerciseType) => {
            comp.exercise = { id: 1, type: exerciseType } as Exercise;
            expect(comp.getPlagarismDetectionTopic()).toEqual(`/topic/${exerciseType}-exercises/1/plagiarism-check`);
        });
    });

    it('should get the minimumSize tootip', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltip');
    });

    it('should get the minimumSize tootip for modeling', () => {
        comp.exercise = { type: ExerciseType.MODELING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipModeling');
    });

    it('should get the minimumSize tootip for text', () => {
        comp.exercise = { type: ExerciseType.TEXT } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipText');
    });

    it('should fetch the plagiarism detection results for modeling exercises', () => {
        comp.exercise = modelingExercise;
        jest.spyOn(modelingExerciseService, 'checkPlagiarism').mockReturnValue(of(modelingPlagiarismResult));

        comp.checkPlagiarism();

        expect(modelingExerciseService.checkPlagiarism).toHaveBeenCalled();
    });

    it('should fetch the plagiarism detection results for programming exercises', () => {
        comp.exercise = programmingExercise;
        jest.spyOn(programmingExerciseService, 'checkPlagiarism').mockReturnValue(of(textPlagiarismResult));

        comp.checkPlagiarism();

        expect(programmingExerciseService.checkPlagiarism).toHaveBeenCalled();
    });

    it('should fetch the plagiarism detection results for text exercises', () => {
        comp.exercise = textExercise;
        jest.spyOn(textExerciseService, 'checkPlagiarism').mockReturnValue(of(textPlagiarismResult));

        comp.checkPlagiarism();

        expect(textExerciseService.checkPlagiarism).toHaveBeenCalled();
    });

    it('should comparisons by similarity', () => {
        comp.sortComparisonsForResult(modelingPlagiarismResult);

        expect(modelingPlagiarismResult.comparisons[0].similarity).toEqual(0.8);
    });

    it('should select a comparison at the given index', () => {
        comp.selectedComparisonIndex = 0;
        comp.selectComparisonAtIndex(1);

        expect(comp.selectedComparisonIndex).toEqual(1);
    });

    it('should download the plagiarism detection results as JSON', () => {
        comp.exercise = modelingExercise;
        comp.plagiarismResult = modelingPlagiarismResult;
        comp.downloadPlagiarismResultsJson();

        expect(downloadFile).toHaveBeenCalled();
    });

    it('should download the plagiarism detection results as CSV', () => {
        comp.exercise = modelingExercise;
        comp.plagiarismResult = modelingPlagiarismResult;
        comp.downloadPlagiarismResultsCsv();

        expect(ExportToCsv).toHaveBeenCalled();
        expect(generateCsv).toHaveBeenCalled();
    });

    it('should get the latest plagiarism result for modeling exercise', fakeAsync(() => {
        comp.exercise = modelingExercise;

        const mockResult = {} as ModelingPlagiarismResult;
        jest.spyOn(modelingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(mockResult));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBe(false);

        tick();

        expect(modelingExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(modelingExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(mockResult);
    }));

    it('should get the latest plagiarism result for programming exercise', fakeAsync(() => {
        comp.exercise = programmingExercise;

        const mockResult = {} as TextPlagiarismResult;
        jest.spyOn(programmingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(mockResult));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBe(false);

        tick();

        expect(programmingExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(programmingExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(mockResult);
    }));

    it('should get the latest plagiarism result for text exercise', fakeAsync(() => {
        comp.exercise = textExercise;

        const mockResult = {} as TextPlagiarismResult;
        jest.spyOn(textExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(mockResult));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBe(false);

        tick();

        expect(textExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(textExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(mockResult);
    }));
});
