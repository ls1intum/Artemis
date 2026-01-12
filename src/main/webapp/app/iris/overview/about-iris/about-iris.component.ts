import { ChangeDetectionStrategy, Component } from '@angular/core';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-about-iris',
    templateUrl: './about-iris.component.html',
    styleUrls: ['about-iris.component.scss'],
    imports: [IrisLogoComponent, ArtemisTranslatePipe, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AboutIrisComponent {
    faRobot = faRobot;
    // How many bullet points each heading has
    bulletPoints: { [key: string]: number } = { '1': 2, '2': 5, '3': 3, '4': 5 };

    objectKeys = Object.keys;
    array = Array;
    protected readonly IrisLogoSize = IrisLogoSize;
}
