import { Component, Input, AfterViewInit } from '@angular/core';
import { FeedbackItem } from 'app/exercises/shared/result/result-detail.component';
import * as _ from 'lodash';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class GraphSegment {
    r: number;
    color: string;
    value: number;
    offset: number;
    points: number;
    legendText: string;
    category: string;
    feedbacks: FeedbackItem[];
}

@Component({
    selector: 'jhi-result-graph',
    templateUrl: './result-graph.component.html',
    styleUrls: ['./result-detail.scss'],
})
export class ResultGraphComponent implements AfterViewInit {
    TWO_PI = Math.PI * 2;

    @Input()
    feedbackList: FeedbackItem[] = [];
    @Input()
    exercise: Exercise;

    graphSegments: GraphSegment[] = [];

    constructor() {}

    ngAfterViewInit(): void {
        const maxPoints = this.exercise.maxScore + (this.exercise.bonusPoints || 0);
        const maxStaticCodeAnalysisPenalty = (this.exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty || 0;

        const groups = _.groupBy(this.feedbackList, 'category');

        const graphSegments = Object.entries(groups)
            .map(([category, feedbacks]) => {
                let points = feedbacks.reduce((sum, f) => sum + (f.credits || 0), 0);
                if (category === 'Code Issue' && points < -maxStaticCodeAnalysisPenalty) {
                    points = -maxStaticCodeAnalysisPenalty;
                }
                return {
                    category,
                    feedbacks,
                    points,
                    value: points / maxPoints,
                    color: this.getColorForCategory(category),
                    legendText: this.getLegendText(category, feedbacks),
                };
            })
            .reduce((segments, group) => {
                if (segments.length === 0) {
                    return [
                        {
                            ...group,
                            r: 40,
                            value: Math.abs(group.value),
                            offset: Math.min(0, group.value),
                        },
                    ];
                } else {
                    const lastSegment = segments[segments.length - 1];
                    let value = group.value;
                    let offset = lastSegment.offset + lastSegment.value;
                    if (group.value > 0 && offset + value > 1) {
                        value = 1 - offset;
                    } else if (value < 0) {
                        value = -value;
                        offset -= value;
                    }
                    return [
                        ...segments,
                        {
                            ...group,
                            r: lastSegment.r - 10,
                            value,
                            offset,
                        },
                    ];
                }
            }, [] as GraphSegment[]);

        if (graphSegments.length > 1) {
            const points = Math.max(
                0,
                Math.min(
                    maxPoints,
                    graphSegments.reduce((sum, s) => sum + s.points, 0),
                ),
            );
            const value = Math.max(0, Math.min(1, points / maxPoints));
            graphSegments.push({
                legendText: `Overall result`,
                color: '#5cbbca',
                r: graphSegments[graphSegments.length - 1].r - 10,
                value,
                points,
                offset: 0,
                category: '',
                feedbacks: [],
            });
        }

        console.log(graphSegments);

        setTimeout(() => (this.graphSegments = graphSegments));
    }

    private getLegendText(category: string, feedbacks: FeedbackItem[]) {
        if (category === 'Code Issue') {
            return `${feedbacks.length} code issues`;
        } else if (category === 'Test Case') {
            return `${feedbacks.filter((f) => f.positive).length} passed tests`;
        } else {
            return `${feedbacks.length} ${category} feedbacksP`;
        }
    }

    private getColorForCategory(category: string) {
        switch (category) {
            case 'Code Issue':
                return '#e5cc69';
            case 'Test Case':
                return '#69e082';
            case 'Tutor':
                return '#78b1e3';
            default:
                return '#ef7885';
        }
    }
}
