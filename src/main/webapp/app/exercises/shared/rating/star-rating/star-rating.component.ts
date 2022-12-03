import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { Subject } from 'rxjs';

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'star-rating',
    template: `<div #starMain></div>`,
    styleUrls: ['./star-rating.component.scss'],
    encapsulation: ViewEncapsulation.ShadowDom,
})
export class StarRatingComponent {
    private stars: Array<HTMLElement> = [];

    private _checkedColor: string;
    private _unCheckedColor: string;
    private _value: number;
    private _size: string;
    private _readOnly = false;
    private _totalStars = 5;

    private onStarsCountChange: Subject<number>;
    private onValueChange: Subject<number>;
    private onCheckedColorChange: Subject<string>;
    private onUnCheckedColorChange: Subject<string>;
    private onSizeChange: Subject<string>;
    private onReadOnlyChange: Subject<boolean>;

    private static readonly VAR_CHECKED_COLOR: string = '--checkedColor';
    private static readonly VAR_UNCHECKED_COLOR: string = '--unCheckedColor';
    private static readonly VAR_SIZE: string = '--size';
    private static readonly VAR_HALF_WIDTH: string = '--halfWidth';
    private static readonly VAR_HALF_MARGIN: string = '--halfMargin';
    private static readonly CLS_CHECKED_STAR: string = 'on';
    private static readonly CLS_DEFAULT_STAR: string = 'star';
    private static readonly CLS_HALF_STAR: string = 'half';

    @ViewChild('starMain', { static: true }) private mainElement: ElementRef;

    constructor() {
        this.onStarsCountChange = new Subject();
        this.onStarsCountChange.subscribe(() => {
            this.setStars();
            this.generateRating(true);
            this.applySizeAllStars();
            this.applyColorStyleAllStars(false);
            this.addEvents();
        });

        this.onValueChange = new Subject();
        this.onValueChange.subscribe(() => {
            this.generateRating();
            this.applySizeAllStars();
        });

        this.onCheckedColorChange = new Subject();
        this.onCheckedColorChange.subscribe(() => {
            this.applyColorStyleAllStars(true);
        });

        this.onUnCheckedColorChange = new Subject();
        this.onUnCheckedColorChange.subscribe(() => {
            this.applyColorStyleAllStars(false);
        });

        this.onSizeChange = new Subject();
        this.onSizeChange.subscribe(() => {
            this.applySizeAllStars();
        });

        this.onReadOnlyChange = new Subject();
        this.onReadOnlyChange.subscribe(() => {
            if (this.readOnly) {
                this.makeReadOnly();
            } else {
                this.makeEditable();
            }
        });
    }

    @Output() rate: EventEmitter<{ oldValue: number; newValue: number; starRating: StarRatingComponent }> = new EventEmitter();

    @Input() set checkedColor(value: string) {
        this._checkedColor = value;
        if (this._checkedColor) {
            this.onCheckedColorChange.next(this._checkedColor);
        }
    }

    get checkedColor(): string {
        return this._checkedColor;
    }

    @Input() set uncheckedColor(value: string) {
        this._unCheckedColor = value;
        if (this._unCheckedColor) {
            this.onUnCheckedColorChange.next(this._unCheckedColor);
        }
    }

    get uncheckedColor(): string {
        return this._unCheckedColor;
    }

    @Input() set value(value: number) {
        value = !value ? 0 : value;
        this._value = value;
        if (this._value >= 0) {
            this.onValueChange.next(this._value);
        }
    }

    get value(): number {
        return this._value;
    }

    @Input() set size(value: string) {
        value = !value || value === '0px' ? '24px' : value;
        this._size = value;
        this.onSizeChange.next(this._size);
    }

    get size(): string {
        return this._size.concat(!this._size.includes('px') ? 'px' : '');
    }

    @Input() set readOnly(value: boolean) {
        this._readOnly = value;
        this.onReadOnlyChange.next(value);
    }

    get readonly(): boolean {
        return String(this._readOnly) === 'true';
    }

    @Input() set totalStars(value: number) {
        this._totalStars = value <= 0 ? 5 : Math.round(value);
        this.onStarsCountChange.next(this._totalStars);
    }

    get totalStars(): number {
        return this._totalStars;
    }

    private makeEditable() {
        if (!this.mainElement) {
            return;
        }
        this.mainElement.nativeElement.style.cursor = 'pointer';
        this.mainElement.nativeElement.title = this.value;
        this.stars.forEach((star: HTMLElement) => {
            star.style.cursor = 'pointer';
            star.title = star.dataset.index!;
        });
    }

    private makeReadOnly() {
        if (!this.mainElement) {
            return;
        }
        this.mainElement.nativeElement.style.cursor = 'default';
        this.mainElement.nativeElement.title = this.value;
        this.stars.forEach((star: HTMLElement) => {
            star.style.cursor = 'default';
            star.title = '';
        });
    }

    private addEvents() {
        if (!this.mainElement) {
            return;
        }
        this.mainElement.nativeElement.addEventListener('mouseleave', this.offStar.bind(this));
        this.mainElement.nativeElement.style.cursor = 'pointer';
        this.mainElement.nativeElement.title = this.value;
        this.stars.forEach((star: HTMLElement) => {
            star.addEventListener('click', this.onRate.bind(this));
            star.addEventListener('mouseenter', this.onStar.bind(this));
            star.style.cursor = 'pointer';
            star.title = star.dataset.index!;
        });
    }

    private onRate(event: MouseEvent) {
        if (this.readOnly) {
            return;
        }
        const star: HTMLElement = <HTMLElement>event.target;
        const oldValue = this.value;
        this.value = parseInt(star.dataset.index!, 10);
        const rateValues = { oldValue, newValue: this.value, starRating: this };
        this.rate.emit(rateValues);
    }

    private onStar(event: MouseEvent) {
        if (this.readOnly) {
            return;
        }
        const star: HTMLElement = <HTMLElement>event.target;
        const currentIndex = parseInt(star.dataset.index!, 10);

        for (let index = 0; index < currentIndex; index++) {
            this.stars[index].className = '';
            StarRatingComponent.addDefaultClass(this.stars[index]);
            StarRatingComponent.addCheckedStarClass(this.stars[index]);
        }

        for (let index = currentIndex; index < this.stars.length; index++) {
            this.stars[index].className = '';
            StarRatingComponent.addDefaultClass(this.stars[index]);
        }
    }

    private offStar() {
        this.generateRating();
    }

    private static addDefaultClass(star: Element) {
        star.classList.add(StarRatingComponent.CLS_DEFAULT_STAR);
    }

    private static addCheckedStarClass(star: Element) {
        star.classList.add(StarRatingComponent.CLS_CHECKED_STAR);
    }

    private static addHalfStarClass(star: Element) {
        star.classList.add(StarRatingComponent.CLS_HALF_STAR);
    }

    private setStars() {
        if (!this.mainElement) {
            return;
        }
        const starContainer: HTMLDivElement = this.mainElement.nativeElement;
        const maxStars = [...Array(this._totalStars).keys()];
        this.stars.length = 0;
        starContainer.innerHTML = '';
        maxStars.forEach((starNumber) => {
            const starElement: HTMLSpanElement = document.createElement('span');
            starElement.dataset.index = (starNumber + 1).toString();
            starElement.title = starElement.dataset.index;
            starContainer.appendChild(starElement);
            this.stars.push(starElement);
        });
    }

    private applySizeAllStars() {
        if (this._size) {
            if (this.stars.length === 0) {
                this.setStars();
            }

            const newSize = this.size.match(/\d+/)![0];
            const halfSize = (parseInt(newSize, 10) * 10) / 24;
            const halfMargin = 0 - (parseInt(newSize, 10) * 20) / 24;

            this.stars.forEach((star: HTMLElement) => {
                star.style.setProperty(StarRatingComponent.VAR_SIZE, this.size);
                if (star.classList.contains(StarRatingComponent.CLS_HALF_STAR)) {
                    star.style.setProperty(StarRatingComponent.VAR_HALF_WIDTH, `${halfSize}px`);
                    star.style.setProperty(StarRatingComponent.VAR_HALF_MARGIN, `${halfMargin}px`);
                }
            });
        }
    }

    private applyColorStyleAllStars(setChecked: boolean) {
        if (this.stars.length === 0) {
            this.setStars();
        }
        this.stars.forEach((star: HTMLElement) => {
            if (setChecked) {
                this.applyCheckedColorStyle(star);
            } else {
                this.applyUnCheckedColorStyle(star);
            }
        });
    }

    private applyColorStyle(starElement: HTMLSpanElement) {
        this.applyCheckedColorStyle(starElement);
        this.applyUnCheckedColorStyle(starElement);
    }

    private applyCheckedColorStyle(starElement: HTMLSpanElement) {
        starElement.style.setProperty(StarRatingComponent.VAR_CHECKED_COLOR, this._checkedColor);
    }

    private applyUnCheckedColorStyle(starElement: HTMLSpanElement) {
        starElement.style.setProperty(StarRatingComponent.VAR_UNCHECKED_COLOR, this._unCheckedColor);
    }

    private generateRating(forceGenerate = false) {
        if (!this.mainElement) {
            return;
        }
        if (this.readOnly && !forceGenerate) {
            return;
        }

        if (this.stars.length === 0) {
            this.setStars();
        }
        this.mainElement.nativeElement.title = this.value;

        let hasDecimals: boolean = !!(Number.parseFloat(this.value.toString()) % 1).toString().substring(3, 2);

        this.stars.forEach((star: HTMLElement, i: number) => {
            star.className = '';
            this.applyColorStyle(star);
            StarRatingComponent.addDefaultClass(star);

            if (this.value > i) {
                // star on
                StarRatingComponent.addCheckedStarClass(star);
            } else {
                // half star
                if (hasDecimals) {
                    StarRatingComponent.addHalfStarClass(star);
                    hasDecimals = false;
                }
            }
        });
    }
}
