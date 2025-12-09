import { Color, ScaleType } from '@swimlane/ngx-charts';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Injectable } from '@angular/core';
import { ChartData } from 'app/exercise/feedback/chart/feedback-chart-data';

@Injectable({ providedIn: 'root' })
export class FeedbackChartService {
    create = (feedbackNodes: FeedbackNode[], exercise: Exercise): ChartData => {
        const summarizedNodes = this.summarizePoints(feedbackNodes);
        const results: NgxChartsMultiSeriesDataEntry[] = [
            {
                name: 'scores',
                series: summarizedNodes.map((node: FeedbackNode) => ({
                    name: node.name,
                    value: this.calculatePercentage(node, exercise),
                })),
            },
        ];
        const scheme: Color = {
            name: 'Feedback Detail',
            selectable: true,
            group: ScaleType.Ordinal,
            domain: summarizedNodes.map((node) => `var(--bs-${node.color})`),
        };

        return {
            xScaleMax: 100,
            results,
            scheme,
        };
    };

    /**
     * Subtracts negative credits from positive ones. This is to make space for the visualization of point deductions
     * @param feedbackNodes
     * @return An array with feedback items in the following order: [...positive, ...neutral, ...negative]
     */
    private summarizePoints = (feedbackNodes: FeedbackNode[]): FeedbackNode[] => {
        const [positive, neutral, negative] = this.separateByCredits(feedbackNodes.slice());
        const sumPositive = this.sumCredits(positive);
        const sumNegative = this.sumCredits(negative);

        if (sumPositive + sumNegative < 0) {
            return this.clearCredits(feedbackNodes);
        }

        const positiveSubtracted = this.subtractCredits(positive, sumNegative);

        return [...positiveSubtracted, ...neutral, ...this.absCredits(negative)];
    };

    private subtractCredits = (feedbackNodes: FeedbackNode[], subtrahend: number): FeedbackNode[] => {
        return feedbackNodes.map((node) => {
            let credits = 0;
            const current = node.credits ?? 0;
            if (current + subtrahend >= 0) {
                credits = current + subtrahend;
            }

            subtrahend = Math.min(subtrahend + current, 0);

            return Object.assign({}, node, { credits });
        });
    };

    /*
     * Separates a list of feedback nodes by node credits. Has runtime of O(3n)
     * @param feedbackNodes
     * @return Tuple with values [Positive, Neutral, Negative]
     */
    private separateByCredits = (feedbackNodes: FeedbackNode[]): [FeedbackNode[], FeedbackNode[], FeedbackNode[]] => {
        return [
            feedbackNodes.filter((node) => (node.credits ?? 0) > 0),
            feedbackNodes.filter((node) => (node.credits ?? 0) === 0),
            feedbackNodes.filter((node) => (node.credits ?? 0) < 0),
        ];
    };

    private sumCredits = (feedbackNodes: FeedbackNode[]) => {
        return feedbackNodes.reduce((acc, node) => (node.credits ?? 0) + acc, 0);
    };

    /*
     * Sets credits in nodes to absolute value
     */
    private absCredits = (feedbackNodes: FeedbackNode[]) => {
        return feedbackNodes.map((node) => Object.assign({}, node, { credits: Math.abs(node.credits ?? 0) }));
    };

    /*
     * Sets credits to 0 for all feedback nodes
     */
    private clearCredits = (feedbackNodes: FeedbackNode[]) => {
        return feedbackNodes.map((node) => Object.assign({}, node, { credits: 0 }));
    };

    private capCredits = (credits: number, maxCredits?: number): number => {
        // no maxCredits or credits and maxCredits do not have the same sign;
        if (!maxCredits || credits * maxCredits < 0) {
            return credits;
        }

        const absCredits = Math.abs(credits);
        const absMaxCredits = Math.abs(maxCredits);
        return Math.sign(credits) * Math.min(absCredits, absMaxCredits);
    };

    private calculatePercentage = (node: FeedbackNode, exercise: Exercise) => {
        const appliedCredits = this.capCredits(node.credits ?? 0, node.maxCredits);
        return roundScorePercentSpecifiedByCourseSettings(appliedCredits / exercise.maxPoints!, getCourseFromExercise(exercise));
    };
}
