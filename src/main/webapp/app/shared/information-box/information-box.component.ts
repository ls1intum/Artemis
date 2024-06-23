import { Component, Input } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

export interface InformationBox {
    title: string;
    content: string | number;
    contentComponent?: string;
    tooltip?: string;
    tooltipParams?: Record<string, string | undefined>;
    contentColor?: string;
}

@Component({
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    constructor() {}

    @Input() informationBoxData: InformationBox;
}
