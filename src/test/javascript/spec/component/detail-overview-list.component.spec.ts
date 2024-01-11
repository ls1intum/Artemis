import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { TranslatePipeMock } from '../helpers/mocks/service/mock-translate.service';
import { MockNgbModalService } from '../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../helpers/mocks/service/mock-alert.service';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

const sections = [
    {
        headline: 'headline.1',
        details: [
            {
                type: DetailType.Text,
                title: 'text',
                data: { text: 'text' },
            },
            false,
        ],
    },
] as DetailOverviewSection[];

describe('DetailOverviewList', () => {
    let component: DetailOverviewListComponent;
    let fixture: ComponentFixture<DetailOverviewListComponent>;
    let modalService: NgbModal;
    let modelingService: ModelingExerciseService;
    let alertServide: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [DetailOverviewListComponent, TranslatePipeMock],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: ModelingExerciseService, useValue: { convertToPdf: jest.fn() } },
            ],
        })
            .overrideTemplate(DetailOverviewListComponent, '')
            .compileComponents()
            .then(() => {
                modalService = fixture.debugElement.injector.get(NgbModal);
                modelingService = fixture.debugElement.injector.get(ModelingExerciseService);
                alertServide = fixture.debugElement.injector.get(AlertService);
            });

        fixture = TestBed.createComponent(DetailOverviewListComponent);
        component = fixture.componentInstance;
    });

    it('should initialize', () => {
        component.sections = sections;
        fixture.detectChanges();
        expect(component.headlines).toStrictEqual([{ id: 'headline-1', translationKey: 'headline.1' }]);
        expect(component.headlinesRecord).toStrictEqual({ 'headline.1': 'headline-1' });
        expect(DetailOverviewListComponent).not.toBeNull();
    });

    it('should open git diff modal', () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        component.showGitDiff(undefined as unknown as ProgrammingExerciseGitDiffReport);
        expect(modalSpy).toHaveBeenCalledOnce();
    });

    it('should download apollon Diagram', () => {
        const downloadSpy = jest.spyOn(modelingService, 'convertToPdf').mockReturnValue(of(new HttpResponse({ body: new Blob() })));
        component.downloadApollonDiagramAsPDf('{}', 'title');
        expect(downloadSpy).toHaveBeenCalledOnce();
    });

    it('should error on download apollon Diagram fail', () => {
        jest.spyOn(modelingService, 'convertToPdf').mockReturnValue(throwError(new HttpResponse({ body: new Blob() })));
        const errorSpy = jest.spyOn(alertServide, 'error');
        component.downloadApollonDiagramAsPDf('{}', 'title');
        expect(errorSpy).toHaveBeenCalledOnce();
    });
});
