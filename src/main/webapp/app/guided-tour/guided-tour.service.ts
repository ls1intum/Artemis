import { ErrorHandler, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { cloneDeep } from 'lodash';
import { JhiAlertService } from 'ng-jhipster';
import { fromEvent, Observable, of, Subject } from 'rxjs';
import { debounceTime } from 'rxjs/internal/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { GuidedTourSettings } from 'app/guided-tour/guided-tour-settings.model';
import { ContentType, GuidedTour, Orientation, OrientationConfiguration, TourStep } from './guided-tour.constants';

export type EntityResponseType = HttpResponse<GuidedTourSettings>;

@Injectable()
export class GuidedTourService {
    public resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';

    public guidedTourCurrentStepStream: Observable<TourStep>;
    public guidedTourOrbShowingStream: Observable<boolean>;
    public currentTourSteps: TourStep[];
    public guidedTourSettings: GuidedTourSettings;

    private _guidedTourCurrentStepSubject = new Subject<TourStep>();
    private _guidedTourOrbShowingSubject = new Subject<boolean>();
    private _currentTourStepIndex = 0;
    private _currentTour: GuidedTour | undefined;
    private _onFirstStep = true;
    private _onLastStep = true;
    private _onResizeMessage = false;

    constructor(public errorHandler: ErrorHandler, private http: HttpClient, private jhiAlertService: JhiAlertService) {
        this.guidedTourCurrentStepStream = this._guidedTourCurrentStepSubject.asObservable();
        this.guidedTourOrbShowingStream = this._guidedTourOrbShowingSubject.asObservable();

        fromEvent(window, 'resize')
            .pipe(debounceTime(200))
            .subscribe(() => {
                if (this._currentTour && this._currentTourStepIndex > -1) {
                    if (this._currentTour.minimumScreenSize && this._currentTour.minimumScreenSize >= window.innerWidth) {
                        this._onResizeMessage = true;
                        this._guidedTourCurrentStepSubject.next({
                            headline: 'Please resize',
                            headlineTranslateKey: '',
                            contentType: ContentType.TEXT,
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

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    public getOverviewTour(): Observable<GuidedTour> | null {
        return of(courseOverviewTour);
    }

    public nextStep(): void {
        if (this._currentTour) {
            const currentStep = this._currentTour.steps[this._currentTourStepIndex];
            if (currentStep.closeAction) {
                currentStep.closeAction();
            }
            if (this._currentTour.steps[this._currentTourStepIndex + 1]) {
                this._currentTourStepIndex++;
                this._setFirstAndLast();
                if (currentStep.action) {
                    currentStep.action();

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
                this.updateGuidedTourSettings(this._currentTour.settingsId, false).subscribe(guidedTourSettings => {
                    if (guidedTourSettings.body) {
                        this.guidedTourSettings = guidedTourSettings.body;
                    }
                });
                this.resetTour();
            }
        }
    }

    public backStep(): void {
        if (this._currentTour) {
            const currentStep = this._currentTour.steps[this._currentTourStepIndex];
            if (currentStep.closeAction) {
                currentStep.closeAction();
            }
            if (this._currentTour.steps[this._currentTourStepIndex - 1]) {
                this._currentTourStepIndex--;
                this._setFirstAndLast();
                if (currentStep.action) {
                    currentStep.action();
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
    }

    public skipTour(): void {
        if (this._currentTour) {
            if (this._currentTour.skipCallback) {
                this._currentTour.skipCallback(this._currentTourStepIndex);
            }
            this.resetTour();
        }
    }

    public resetTour(): void {
        document.body.classList.remove('tour-open');
        this._currentTour = undefined;
        this._currentTourStepIndex = 0;
        this._guidedTourCurrentStepSubject.next(undefined);
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
            const currentStep = this._currentTour.steps[this._currentTourStepIndex];
            if (currentStep.action) {
                currentStep.action();
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
        if (this._currentTour) {
            this._onLastStep = this._currentTour.steps.length - 1 === this._currentTourStepIndex;
            this._onFirstStep = this._currentTourStepIndex === 0;
        }
    }

    private _checkSelectorValidity(): boolean {
        if (this._currentTour) {
            if (this._currentTour.steps[this._currentTourStepIndex].selector) {
                const selectedElement = document.querySelector(this._currentTour.steps[this._currentTourStepIndex].selector!);
                if (!selectedElement) {
                    this.errorHandler.handleError(
                        // If error handler is configured this should not block the browser.
                        new Error(
                            `Error finding selector ${this._currentTour.steps[this._currentTourStepIndex].selector} on step ${this._currentTourStepIndex + 1} during guided tour: ${
                                this._currentTour.settingsId
                            }`,
                        ),
                    );
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
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
        if (this._currentTour) {
            return this._currentTour && (this._currentTour.preventBackdropFromAdvancing ? this._currentTour.preventBackdropFromAdvancing : false);
        }
        return false;
    }

    private getPreparedTourStep(index: number): TourStep | undefined {
        if (this._currentTour) {
            return this.setTourOrientation(this._currentTour.steps[index]);
        } else {
            return undefined;
        }
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

    private findGuidedTourSettings(): Observable<GuidedTourSettings | undefined> {
        return this.http.get<GuidedTourSettings>(this.resourceUrl, { observe: 'response' }).map((res: EntityResponseType) => {
            if (res.body) {
                return res.body;
            }
        });
    }

    public updateGuidedTourSettings(settingName: string, settingValue: boolean): Observable<EntityResponseType> {
        this.guidedTourSettings[settingName] = settingValue;
        return this.http.put<GuidedTourSettings>(this.resourceUrl, this.guidedTourSettings, { observe: 'response' });
    }

    public getGuidedTourSettings() {
        this.findGuidedTourSettings().subscribe(guidedTourSettings => {
            if (guidedTourSettings) {
                this.guidedTourSettings = guidedTourSettings;
            }
        });
    }
}
