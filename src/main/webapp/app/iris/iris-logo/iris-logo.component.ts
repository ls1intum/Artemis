import { Component, Input, OnInit } from '@angular/core';

export enum IrisLogoSize {
    SMALL = 'small',
    BIG = 'big',
}

@Component({
    selector: 'jhi-iris-logo',
    templateUrl: './iris-logo.component.html',
    styleUrls: ['./iris-logo.component.scss'],
})
export class IrisLogoComponent implements OnInit {
    @Input()
    size: IrisLogoSize = IrisLogoSize.BIG;

    logoUrl: string;
    classList: string;

    ngOnInit() {
        if (this.size === IrisLogoSize.SMALL) {
            this.logoUrl = 'public/images/iris/iris-logo-small.png';
            this.classList = 'small';
        } else {
            this.logoUrl = 'public/images/iris/iris-logo-big.png';
            this.classList = 'big img-fluid';
        }
    }
}
