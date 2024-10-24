import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';

export enum IrisLogoSize {
    FLUID = 'fluid',
    SMALL = 'small',
    MEDIUM = 'medium',
    BIG = 'big',
}

export enum IrisLogoLookDirection {
    LEFT = 'left',
    RIGHT = 'right',
}

@Component({
    selector: 'jhi-iris-logo',
    templateUrl: './iris-logo.component.html',
    styleUrls: ['./iris-logo.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
})
export class IrisLogoComponent implements OnInit {
    @Input()
    size: IrisLogoSize | number = IrisLogoSize.BIG;

    @Input()
    look: IrisLogoLookDirection = IrisLogoLookDirection.RIGHT;

    logoUrl: string;
    classList: string;

    ngOnInit() {
        if (this.size === IrisLogoSize.SMALL) {
            this.logoUrl = 'public/images/iris/iris-logo-small.png';
            this.classList = 'small';
        } else if (this.size === IrisLogoSize.MEDIUM) {
            this.logoUrl = `public/images/iris/iris-logo-big-${this.look}.png`;
            this.classList = 'medium';
        } else if (this.size === IrisLogoSize.BIG) {
            this.logoUrl = `public/images/iris/iris-logo-big-${this.look}.png`;
            this.classList = 'big img-fluid';
        } else if (this.size === IrisLogoSize.FLUID) {
            this.logoUrl = `public/images/iris/iris-logo-big-${this.look}.png`;
            this.classList = 'fluid';
        }
    }
}
