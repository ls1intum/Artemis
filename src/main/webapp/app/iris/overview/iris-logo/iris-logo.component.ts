import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { NgOptimizedImage } from '@angular/common';

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
    imports: [NgOptimizedImage, ArtemisTranslatePipe],
})
export class IrisLogoComponent {
    protected readonly IrisLogoSize = IrisLogoSize;
    size = input<IrisLogoSize | number>(IrisLogoSize.BIG);
    look = input<IrisLogoLookDirection>(IrisLogoLookDirection.RIGHT);
    spinning = input<boolean>(false);
    /** Hide a logo from assistive technology when adjacent text already identifies Iris. */
    readonly decorative = input(false);

    logoUrl = computed(() => {
        if (this.size() === IrisLogoSize.SMALL) {
            return 'public/images/iris/iris-logo-small.png';
        }
        return `public/images/iris/iris-logo-big-${this.look()}.png`;
    });

    numericHeight = computed(() => {
        const s = this.size();
        return typeof s === 'number' ? s : undefined;
    });

    classList = computed(() => {
        switch (this.size()) {
            case IrisLogoSize.TEXT:
                return 'iris-logo--text';
            case IrisLogoSize.SMALL:
                return 'iris-logo--small';
            case IrisLogoSize.MEDIUM:
                return 'iris-logo--medium';
            case IrisLogoSize.BIG:
                return 'iris-logo--big';
            case IrisLogoSize.FLUID:
                return 'iris-logo--fluid';
            default:
                return '';
        }
    });
}
