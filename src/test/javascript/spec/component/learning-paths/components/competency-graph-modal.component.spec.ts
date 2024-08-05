import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyGraphModalComponent } from 'app/course/learning-paths/components/competency-graph-modal/competency-graph-modal.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO } from 'app/entities/competency/learning-path.model';
import { By } from '@angular/platform-browser';

describe('CompetencyGraphModalComponent', () => {
    let component: CompetencyGraphModalComponent;
    let fixture: ComponentFixture<CompetencyGraphModalComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getCompetencyPathSpy: jest.SpyInstance;

    const learningPathId = 1;

    const competencyGraph = <CompetencyGraphDTO>{
        nodes: [
            {
                id: '1',
                label: 'Node 1',
            } as CompetencyGraphNodeDTO,
            {
                id: '2',
                label: 'Node 2',
            } as CompetencyGraphNodeDTO,
        ],
        edges: [
            {
                source: '1',
                target: '2',
            } as CompetencyGraphEdgeDTO,
        ],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyGraphModalComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                MockProvider(NgbActiveModal),
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        getCompetencyPathSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencyGraph').mockReturnValue(Promise.resolve(competencyGraph));

        fixture = TestBed.createComponent(CompetencyGraphModalComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
        expect(component.learningPathId()).toBe(learningPathId);
    });

    it('should load data', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCompetencyPathSpy).toHaveBeenCalledExactlyOnceWith(learningPathId);
        expect(component.competencyGraph()).toEqual(competencyGraph);
    });

    it('should show alert on failed data loading', async () => {
        getCompetencyPathSpy.mockReturnValue(Promise.reject());
        const errorSpy = jest.spyOn(alertService, 'error');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should close modal', () => {
        const closeSpy = jest.spyOn(component, 'closeModal');
        const closeButton = fixture.debugElement.query(By.css('#close-button'));
        closeButton.nativeElement.click();

        fixture.detectChanges();

        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
