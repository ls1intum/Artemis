import { Component, Input, OnInit } from '@angular/core';
import { FeedbackItem } from 'app/exercises/shared/result/result-detail.component';
import * as _ from 'lodash';

export class GraphSegment {
    r: number;
    color: string;
    value: number;
    offset: number;
    group: any;
}

@Component({
    selector: 'jhi-result-graph',
    templateUrl: './result-graph.component.html',
    styleUrls: ['./result-detail.scss'],
})
export class ResultGraphComponent implements OnInit {
    TWO_PI = Math.PI * 2;

    @Input()
    feedbackList: FeedbackItem[] = [];
    @Input()
    maxPoints = 100;

    graphSegments: GraphSegment[] = [];

    constructor() {}

    ngOnInit(): void {
        const groups = _.groupBy(this.feedbackList, 'category');
        const graphSegments = Object.entries(groups)
            .map(([category, feedbacks]) => {
                const points = feedbacks.reduce((sum, f) => sum + (f.credits || 0), 0);
                return { category, feedbacks, points, value: points / this.maxPoints };
            })
            .reduce((segments, group, i) => {
                if (segments.length === 0) {
                    return [
                        {
                            r: 40,
                            color: this.getColor(i),
                            value: Math.abs(group.value),
                            offset: Math.min(0, group.value),
                            group,
                        },
                    ];
                } else {
                    const lastSegment = segments[segments.length - 1];
                    return [
                        ...segments,
                        {
                            r: lastSegment.r - 5,
                            color: this.getColor(i),
                            value: Math.abs(group.value),
                            offset: lastSegment.offset + lastSegment.value + Math.min(0, group.value),
                            group,
                        },
                    ];
                }
            }, [] as GraphSegment[]);

        graphSegments.push({
            r: graphSegments[graphSegments.length - 1].r - 5,
            color: this.getColor(graphSegments.length),
            value: Math.max(
                0,
                graphSegments.reduce((sum, s) => sum + s.group.value, 0),
            ),
            offset: 0,
            group: null,
        });

        console.log(graphSegments);

        this.graphSegments = graphSegments;
    }

    private getColor(i: number) {
        return ['#ff0000', '#00ff00', '#0000ff'][i];
    }
}
