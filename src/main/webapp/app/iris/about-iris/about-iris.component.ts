import { Component } from '@angular/core';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoSize } from '../iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-about-iris',
    templateUrl: './about-iris.component.html',
    styleUrls: ['about-iris.component.scss'],
})
export class AboutIrisComponent {
    faRobot = faRobot;
    // How many bullet points each heading has
    bulletPoints: { [key: string]: number } = { '1': 2, '2': 5, '3': 3, '4': 5 };

    objectKeys = Object.keys;
    array = Array;
    protected readonly IrisLogoSize = IrisLogoSize;
}
