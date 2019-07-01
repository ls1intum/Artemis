import { debounceTime } from 'rxjs/internal/operators';
import { ErrorHandler, Injectable } from '@angular/core';
import { Observable, Subject, fromEvent, of } from 'rxjs';
import { GuidedTour, TourStep, Orientation, OrientationConfiguration } from './guided-tour.constants';
import { cloneDeep } from 'lodash';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';

@Injectable()
export class GuidedTourService {
    public guidedTourCurrentStepStream: Observable<TourStep>;
    public guidedTourOrbShowingStream: Observable<boolean>;
    public currentTourSteps: TourStep[];

    private _guidedTourCurrentStepSubject = new Subject<TourStep>();
    private _guidedTourOrbShowingSubject = new Subject<boolean>();
    private _currentTourStepIndex = 0;
    private _currentTour: GuidedTour = null;
    private _onFirstStep = true;
    private _onLastStep = true;
    private _onResizeMessage = false;

    constructor(public errorHandler: ErrorHandler) {
        this.guidedTourCurrentStepStream = this._guidedTourCurrentStepSubject.asObservable();
        this.guidedTourOrbShowingStream = this._guidedTourOrbShowingSubject.asObservable();

        fromEvent(window, 'resize')
            .pipe(debounceTime(200))
            .subscribe(() => {
                if (this._currentTour && this._currentTourStepIndex > -1) {
                    if (this._currentTour.minimumScreenSize && this._currentTour.minimumScreenSize >= window.innerWidth) {
                        this._onResizeMessage = true;
                        this._guidedTourCurrentStepSubject.next({
                            title: 'Please resize',
                            titleTranslateKey: '',
                            content:
                                'You have resized the tour to a size that is too small to continue. Please resize the browser to a larger size to continue the tour or close the tour.',
                            contentTranslateKey: '',
                        });
                    } else {
                        this._onResizeMessage = false;
                        this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
                    }
                }
            });
    }

    public getOverviewTour(): Observable<GuidedTour> {
        return of(courseOverviewTour);
    }

    public nextStep(): void {
        if (this._currentTour.steps[this._currentTourStepIndex].closeAction) {
            this._currentTour.steps[this._currentTourStepIndex].closeAction();
        }
        if (this._currentTour.steps[this._currentTourStepIndex + 1]) {
            this._currentTourStepIndex++;
            this._setFirstAndLast();
            if (this._currentTour.steps[this._currentTourStepIndex].action) {
                this._currentTour.steps[this._currentTourStepIndex].action();
                // Usually an action is opening something so we need to give it time to render.
                setTimeout(() => {
                    if (this._checkSelectorValidity()) {
                        this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
                    } else {
                        this.nextStep();
                    }
                });
            } else {
                if (this._checkSelectorValidity()) {
                    this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
                } else {
                    this.nextStep();
                }
            }
        } else {
            if (this._currentTour.completeCallback) {
                this._currentTour.completeCallback();
            }
            this.resetTour();
        }
    }

    public backStep(): void {
        if (this._currentTour.steps[this._currentTourStepIndex].closeAction) {
            this._currentTour.steps[this._currentTourStepIndex].closeAction();
        }
        if (this._currentTour.steps[this._currentTourStepIndex - 1]) {
            this._currentTourStepIndex--;
            this._setFirstAndLast();
            if (this._currentTour.steps[this._currentTourStepIndex].action) {
                this._currentTour.steps[this._currentTourStepIndex].action();
                setTimeout(() => {
                    if (this._checkSelectorValidity()) {
                        this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
                    } else {
                        this.backStep();
                    }
                });
            } else {
                if (this._checkSelectorValidity()) {
                    this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
                } else {
                    this.backStep();
                }
            }
        } else {
            this.resetTour();
        }
    }

    public skipTour(): void {
        if (this._currentTour.skipCallback) {
            this._currentTour.skipCallback(this._currentTourStepIndex);
        }
        this.resetTour();
    }

    public resetTour(): void {
        document.body.classList.remove('tour-open');
        this._currentTour = null;
        this._currentTourStepIndex = 0;
        this._guidedTourCurrentStepSubject.next(null);
    }

    public startTour(tour: GuidedTour): void {
        this.currentTourSteps = tour.steps;
        this._currentTour = cloneDeep(tour);
        this._currentTour.steps = this._currentTour.steps.filter(step => !step.skipStep);
        this._currentTourStepIndex = 0;
        this._setFirstAndLast();
        this._guidedTourOrbShowingSubject.next(this._currentTour.useOrb);
        if (this._currentTour.steps.length > 0 && (!this._currentTour.minimumScreenSize || window.innerWidth >= this._currentTour.minimumScreenSize)) {
            if (!this._currentTour.useOrb) {
                document.body.classList.add('tour-open');
            }
            if (this._currentTour.steps[this._currentTourStepIndex].action) {
                this._currentTour.steps[this._currentTourStepIndex].action();
            }
            if (this._checkSelectorValidity()) {
                this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
            } else {
                this.nextStep();
            }
        }
    }

    public activateOrb(): void {
        this._guidedTourOrbShowingSubject.next(false);
        document.body.classList.add('tour-open');
    }

    private _setFirstAndLast(): void {
        this._onLastStep = this._currentTour.steps.length - 1 === this._currentTourStepIndex;
        this._onFirstStep = this._currentTourStepIndex === 0;
    }

    private _checkSelectorValidity(): boolean {
        if (this._currentTour.steps[this._currentTourStepIndex].selector) {
            const selectedElement = document.querySelector(this._currentTour.steps[this._currentTourStepIndex].selector);
            if (!selectedElement) {
                this.errorHandler.handleError(
                    // If error handler is configured this should not block the browser.
                    new Error(
                        `Error finding selector ${this._currentTour.steps[this._currentTourStepIndex].selector} on step ${this._currentTourStepIndex + 1} during guided tour: ${
                            this._currentTour.tourId
                        }`,
                    ),
                );
                return false;
            }
        }
        return true;
    }

    public get onLastStep(): boolean {
        return this._onLastStep;
    }

    public get onFirstStep(): boolean {
        return this._onFirstStep;
    }

    public get onResizeMessage(): boolean {
        return this._onResizeMessage;
    }

    public get currentTourStepDisplay(): number {
        return this._currentTourStepIndex + 1;
    }

    public get currentTourStepCount(): any {
        return this._currentTour && this._currentTour.steps ? this._currentTour.steps.length : 0;
    }

    public get preventBackdropFromAdvancing(): boolean {
        return this._currentTour && this._currentTour.preventBackdropFromAdvancing;
    }

    private getPreparedTourStep(index: number): TourStep {
        return this.setTourOrientation(this._currentTour.steps[index]);
    }

    private setTourOrientation(step: TourStep): TourStep {
        const convertedStep = cloneDeep(step);
        if (convertedStep.orientation && !(typeof convertedStep.orientation === 'string') && (convertedStep.orientation as OrientationConfiguration[]).length) {
            (convertedStep.orientation as OrientationConfiguration[]).sort((a: OrientationConfiguration, b: OrientationConfiguration) => {
                if (!b.maximumSize) {
                    return 1;
                }
                if (!a.maximumSize) {
                    return -1;
                }
                return b.maximumSize - a.maximumSize;
            });

            let currentOrientation: Orientation = Orientation.Top;
            (convertedStep.orientation as OrientationConfiguration[]).forEach((orientationConfig: OrientationConfiguration) => {
                if (!orientationConfig.maximumSize || window.innerWidth <= orientationConfig.maximumSize) {
                    currentOrientation = orientationConfig.orientationDirection;
                }
            });

            convertedStep.orientation = currentOrientation;
        }
        return convertedStep;
    }
}
