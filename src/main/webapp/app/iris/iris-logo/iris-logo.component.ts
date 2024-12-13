import { Component, computed, input } from '@angular/core';

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
    standalone: true,
})
export class IrisLogoComponent {
    size = input<IrisLogoSize | number>(IrisLogoSize.BIG);
    look = input<IrisLogoLookDirection>(IrisLogoLookDirection.RIGHT);

    logoUrl = computed(() => {
        if (this.size() === IrisLogoSize.SMALL) {
            return 'public/images/iris/iris-logo-small.png';
        }
        return `public/images/iris/iris-logo-big-${this.look()}.png`;
    });

    classList = computed(() => {
        if (this.size() === IrisLogoSize.SMALL) {
            return 'small';
        } else if (this.size() === IrisLogoSize.MEDIUM) {
            return 'medium';
        } else if (this.size() === IrisLogoSize.BIG) {
            return 'big img-fluid';
        } else if (this.size() === IrisLogoSize.FLUID) {
            return 'fluid';
        }
        return '';
    });
}
