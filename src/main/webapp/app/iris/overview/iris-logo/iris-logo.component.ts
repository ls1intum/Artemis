import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export enum IrisLogoSize {
    FLUID = 'fluid',
    TEXT = 'text',
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
        switch (this.size()) {
            case IrisLogoSize.TEXT:
                return 'text';
            case IrisLogoSize.SMALL:
                return 'small';
            case IrisLogoSize.MEDIUM:
                return 'medium';
            case IrisLogoSize.BIG:
                return 'big img-fluid';
            case IrisLogoSize.FLUID:
                return 'fluid';
            default:
                return '';
        }
    });
}
