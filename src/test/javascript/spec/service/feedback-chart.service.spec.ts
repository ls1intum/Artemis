import { FeedbackChartService } from 'app/exercises/shared/feedback/chart/feedback-chart.service';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';
import { Exercise } from 'app/entities/exercise.model';
import { Color, ScaleType } from '@swimlane/ngx-charts';

describe('FeedbackChartService', () => {
    let service: FeedbackChartService;
    beforeEach(() => {
        service = new FeedbackChartService();
    });

    function getExercise() {
        return {
            maxPoints: 100,
            bonusPoints: 0,
        } as Exercise;
    }

    it('sets xScaleMax correctly according to maximal points', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [];
        const expected = 100;
        expect(service.create(feedbackNodes, exercise).xScaleMax).toEqual(expected);
    });

    it('extracts color scheme correctly', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [{ color: 'secondary' } as FeedbackNode, { color: 'success' } as FeedbackNode, { color: 'primary' } as FeedbackNode];
        const expected = {
            name: 'Feedback Detail',
            selectable: true,
            group: ScaleType.Ordinal,
            domain: ['var(--bs-secondary)', 'var(--bs-success)', 'var(--bs-primary)'],
        } as Color;

        expect(service.create(feedbackNodes, exercise).scheme).toEqual(expected);
    });
});
