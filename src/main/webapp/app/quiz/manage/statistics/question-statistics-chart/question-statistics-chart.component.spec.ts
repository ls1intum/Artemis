import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuestionStatisticsChartComponent } from './question-statistics-chart.component';

describe('question statistics chart', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('updates the participant label and emits solution/rating changes from the rendered buttons', () => {
        const fixture = TestBed.createComponent(QuestionStatisticsChartComponent);
        fixture.componentRef.setInput('chartData', { labels: ['A'], series: [{ data: [2] }] });
        fixture.componentRef.setInput('chartConfig', {});
        fixture.componentRef.setInput('participants', 2);
        const solution = vi.fn();
        const rating = vi.fn();
        fixture.componentInstance.toggleSolution.subscribe(solution);
        fixture.componentInstance.toggleRated.subscribe(rating);
        fixture.detectChanges();
        const buttons = fixture.nativeElement.querySelectorAll('button');
        buttons[0].click();
        buttons[1].click();
        expect(solution).toHaveBeenCalledOnce();
        expect(rating).toHaveBeenCalledOnce();
        fixture.componentRef.setInput('participants', 7);
        fixture.componentRef.setInput('rated', false);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('7');
        expect(fixture.nativeElement.textContent).toContain('artemisApp.showStatistic.questionStatistic.titleUnrated');
        expect(buttons[1].textContent).toContain('artemisApp.showStatistic.switchToRated');
        fixture.componentRef.setInput('showSolution', true);
        fixture.detectChanges();
        expect(buttons[0].textContent).toContain('artemisApp.showStatistic.dragAndDropQuestionStatistic.hideSampleSolution');
    });
});
