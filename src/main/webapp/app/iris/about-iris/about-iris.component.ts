import { ChangeDetectionStrategy, Component } from '@angular/core';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoComponent, IrisLogoSize } from '../iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { IrisModule } from 'app/iris/iris.module';

@Component({
    selector: 'jhi-about-iris',
    templateUrl: './about-iris.component.html',
    styleUrls: ['about-iris.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [TranslateDirective, ArtemisSharedModule, IrisModule, IrisLogoComponent],
})
export class AboutIrisComponent {
    faRobot = faRobot;
    // How many bullet points each heading has
    bulletPoints: { [key: string]: number } = { '1': 2, '2': 5, '3': 3, '4': 5 };

    objectKeys = Object.keys;
    array = Array;
    protected readonly IrisLogoSize = IrisLogoSize;
}
