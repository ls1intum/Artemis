import { Component, Input } from '@angular/core';

export interface InformationBox {
    title: string;
    content: string | number;
    contentComponent?: string;
    tooltip?: string;
    tooltipParams?: Record<string, string | undefined>;
    contentColor?: string;
}

@Component({
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    constructor() {}

    @Input() informationBoxData: InformationBox;
}
