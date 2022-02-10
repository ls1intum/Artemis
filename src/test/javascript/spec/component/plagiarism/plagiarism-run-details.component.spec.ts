import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SimpleChange } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { PlagiarismRunDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';
import { MockModule } from 'ng-mocks';
import { BarChartModule } from '@swimlane/ngx-charts';

describe('Plagiarism Run Details', () => {
    let comp: PlagiarismRunDetailsComponent;
    let fixture: ComponentFixture<PlagiarismRunDetailsComponent>;

    const plagiarismResult = {
        duration: 5200,
        similarityDistribution: [24, 18, 16, 13, 7, 9, 5, 4, 0, 1],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisPlagiarismModule, TranslateTestingModule, MockModule(BarChartModule)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismRunDetailsComponent);
        comp = fixture.componentInstance;
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
