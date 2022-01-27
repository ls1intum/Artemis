import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SimpleChange } from '@angular/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismRunDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';
import { MockModule, MockPipe } from 'ng-mocks';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('Plagiarism Run Details', () => {
    let comp: PlagiarismRunDetailsComponent;
    let fixture: ComponentFixture<PlagiarismRunDetailsComponent>;

    const plagiarismResult = {
        duration: 5200,
        similarityDistribution: [24, 18, 16, 13, 7, 9, 5, 4, 0, 1],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(BarChartModule)],
            declarations: [PlagiarismRunDetailsComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismRunDetailsComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('updates chart data on changes', () => {
        jest.spyOn(comp, 'updateChartDataSet');

        comp.ngOnChanges({
            plagiarismResult: { currentValue: plagiarismResult } as SimpleChange,
        });

        expect(comp.updateChartDataSet).toHaveBeenCalled();
        for (let i = 0; i < 10; i++) {
            expect(comp.ngxData[i].value).toBe(plagiarismResult.similarityDistribution[i]);
        }
    });

    it('updates the chart data correctly', () => {
        expect(comp.ngxData).toHaveLength(0);

        comp.updateChartDataSet([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);

        expect(comp.ngxData).toHaveLength(10);
    });
});
