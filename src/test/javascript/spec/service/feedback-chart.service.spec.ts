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

    it('should show correct results when only positive credits', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [{ credits: 10 } as FeedbackNode, { credits: 20 } as FeedbackNode];
        const expected = [
            { name: undefined, value: 10 },
            { name: undefined, value: 20 },
        ];

        expect(service.create(feedbackNodes, exercise).results[0].series).toEqual(expected);
    });

    it('should not show net negative results', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [{ credits: 10 } as FeedbackNode, { credits: 20 } as FeedbackNode, { credits: -40 } as FeedbackNode];
        const expected = [{ value: 0 }, { value: 0 }, { value: 0 }];

        expect(service.create(feedbackNodes, exercise).results[0].series).toEqual(expected);
    });

    it('should subtract correctly', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [{ credits: 50 } as FeedbackNode, { credits: 20 } as FeedbackNode, { credits: -40 } as FeedbackNode];
        const expected = [{ value: 10 }, { value: 20 }, { value: 40 }];

        expect(service.create(feedbackNodes, exercise).results[0].series).toEqual(expected);
    });

    it('should subtract correctly with overflow', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [{ credits: 50 } as FeedbackNode, { credits: 20 } as FeedbackNode, { credits: -60 } as FeedbackNode];
        const expected = [{ value: 0 }, { value: 10 }, { value: 60 }];

        expect(service.create(feedbackNodes, exercise).results[0].series).toEqual(expected);
    });

    it('should not show more than max credits', () => {
        const exercise = getExercise();
        const feedbackNodes: FeedbackNode[] = [
            { credits: -3, maxCredits: -2 } as FeedbackNode,
            { credits: 3, maxCredits: 2 } as FeedbackNode,
            { credits: 5, maxCredits: -7 } as FeedbackNode,
            { credits: -5, maxCredits: 7 } as FeedbackNode,
        ];
        const expected = [{ value: 0 }, { value: 0 }, { value: 3 }, { value: 5 }];

        expect(service.create(feedbackNodes, exercise).results[0].series).toEqual(expected);
    });
});
