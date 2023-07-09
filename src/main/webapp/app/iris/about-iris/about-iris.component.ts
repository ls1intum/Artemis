import { Component } from '@angular/core';
import { faRobot } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-about-iris',
    templateUrl: './about-iris.component.html',
    styleUrls: [],
})
export class AboutIrisComponent {
    faRobot = faRobot;
    // How many bullet points each heading has
    bulletPoints = { '1': 2, '2': 5, '3': 3, '4': 4 };

    objectKeys = Object.keys;
    array = Array;
}
