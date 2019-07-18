import { ErrorHandler, Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { cloneDeep } from 'lodash';
import { JhiAlertService } from 'ng-jhipster';
import { fromEvent, Observable, of, Subject } from 'rxjs';
import { debounceTime } from 'rxjs/internal/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { GuidedTourSettings } from 'app/guided-tour/guided-tour-settings.model';
import { ContentType, GuidedTour, Orientation, OrientationConfiguration, TourStep } from './guided-tour.constants';
import { AccountService } from 'app/core';

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

    constructor(public errorHandler: ErrorHandler, private http: HttpClient, private jhiAlertService: JhiAlertService, private accountService: AccountService) {
        this.getGuidedTourSettings();

        this.guidedTourCurrentStepStream = this._guidedTourCurrentStepSubject.asObservable();
        this.guidedTourOrbShowingStream = this._guidedTourOrbShowingSubject.asObservable();

        fromEvent(window, 'resize')
            .pipe(debounceTime(200))
            .subscribe(() => {
                if (this._currentTour && this._currentTourStepIndex > -1) {
                    if (this._currentTour.minimumScreenSize && this._currentTour.minimumScreenSize >= window.innerWidth) {
                        this._onResizeMessage = true;
                        this._guidedTourCurrentStepSubject.next({
                            headlineTranslateKey: 'tour.resize.headline',
                            contentType: ContentType.TEXT,
                            contentTranslateKey: 'tour.resize.content',
                        });
                    } else {
                        this._onResizeMessage = false;
                        this._guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this._currentTourStepIndex));
                    }
                }
            });
    }

    /**
     * Load course overview tour
     */
    public getOverviewTour(): Observable<GuidedTour> {
        return of(courseOverviewTour);
    }

    /**
     * Navigate to next tour step
     */
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

    /**
     * Navigate to previous tour step
     */
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

    /**
     * Skip tour
     */
    public skipTour(): void {
        if (this._currentTour) {
            if (this._currentTour.skipCallback) {
                this._currentTour.skipCallback(this._currentTourStepIndex);
            }
            this.resetTour();
        }
    }

    /**
     * Close tour and remove overlay
     */
    public resetTour(): void {
        document.body.classList.remove('tour-open');
        this._currentTour = undefined;
        this._currentTourStepIndex = 0;
        this._guidedTourCurrentStepSubject.next(undefined);
    }

    /**
     * Start guided tour for given guided tour
     * @param tour  guided tour
     */
    public startTour(tour: GuidedTour): void {
        this.currentTourSteps = tour.steps;

        // adjust tour steps according to permissions
        tour.steps.forEach((step, index) => {
            if (step.permission && !this.accountService.hasAnyAuthorityDirect(step.permission)) {
                this.currentTourSteps.splice(index, 1);
            }
        });

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

    /* Start tour with orb */
    public activateOrb(): void {
        this._guidedTourOrbShowingSubject.next(false);
        document.body.classList.add('tour-open');
    }

    /**
     * Define first and last tour step based on amount of tour steps
     * @private
     */
    private _setFirstAndLast(): void {
        if (this._currentTour) {
            this._onLastStep = this._currentTour.steps.length - 1 === this._currentTourStepIndex;
            this._onFirstStep = this._currentTourStepIndex === 0;
        }
    }

    /* Check if highlighted element is available */
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

    /**
     *  Is last tour step
     */
    public get onLastStep(): boolean {
        return this._onLastStep;
    }

    /**
     * Is first tour step
     */
    public get onFirstStep(): boolean {
        return this._onFirstStep;
    }

    /* Show resize message */
    public get onResizeMessage(): boolean {
        return this._onResizeMessage;
    }

    /* Current tour step number */
    public get currentTourStepDisplay(): number {
        return this._currentTourStepIndex + 1;
    }

    /* Total count of tour steps */
    public get currentTourStepCount(): any {
        return this._currentTour && this._currentTour.steps ? this._currentTour.steps.length : 0;
    }

    /* Prevents the tour from advancing by clicking the backdrop */
    public get preventBackdropFromAdvancing(): boolean {
        if (this._currentTour) {
            return this._currentTour && (this._currentTour.preventBackdropFromAdvancing ? this._currentTour.preventBackdropFromAdvancing : false);
        }
        return false;
    }

    /**
     * Get the tour step with defined orientation
     * @param index current tour step index
     */
    private getPreparedTourStep(index: number): TourStep | undefined {
        if (this._currentTour) {
            return this.setTourOrientation(this._currentTour.steps[index]);
        } else {
            return undefined;
        }
    }

    /**
     * Set orientation of the passed on tour step
     * @param step  passed on tour step of a guided tour
     */
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

    /**
     * Send a GET request for the guided tour settings of the current user
     */
    private findGuidedTourSettings(): Observable<GuidedTourSettings | undefined> {
        return this.http.get<GuidedTourSettings>(this.resourceUrl, { observe: 'response' }).map((res: EntityResponseType) => {
            if (res.body) {
                return res.body;
            }
        });
    }

    /**
     * Send a PUT request to update the guided tour settings of the current user
     * @param settingName   name of the tour setting that is stored in the guided tour settings json in the DB, e.g. showCourseOverviewTour
     * @param settingValue  boolean value that defines if the tour for [settingName] should be displayed automatically
     */
    public updateGuidedTourSettings(settingName: string, settingValue: boolean): Observable<EntityResponseType> {
        this.guidedTourSettings[settingName] = settingValue;
        return this.http.put<GuidedTourSettings>(this.resourceUrl, this.guidedTourSettings, { observe: 'response' });
    }

    /**
     * Subscribe to guided tour settings and store value in service class variable
     */
    public getGuidedTourSettings() {
        this.findGuidedTourSettings().subscribe(guidedTourSettings => {
            if (guidedTourSettings) {
                this.guidedTourSettings = guidedTourSettings;
            }
        });
    }
}
