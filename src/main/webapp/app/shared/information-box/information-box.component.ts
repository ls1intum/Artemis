import { Component, Input } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export interface InformationBox {
    title: string;
    content: string | number;
    contentComponent?: string;
    tooltip?: string;
    tooltipParams?: Record<string, string | undefined>;
    contentColor?: string;
}
// export interface InformationBox {
//     title: string;
//     content: string | number | any;
//     contentType?: string;
//     isContentComponent?: boolean;
//     contentComponent?: any;
//     icon?: IconProp;
//     tooltip?: string;
//     contentColor?: string;
//     tooltipParams?: any;
// }

@Component({
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    @Input() informationBoxData: InformationBox;
}
