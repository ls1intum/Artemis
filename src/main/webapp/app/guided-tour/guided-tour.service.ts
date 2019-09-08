import { ErrorHandler, Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { NavigationStart, Router } from '@angular/router';
import { cloneDeep } from 'lodash';
import { JhiAlertService } from 'ng-jhipster';
import { fromEvent, Observable, Subject } from 'rxjs';
import { debounceTime } from 'rxjs/internal/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';
import { GuidedTourState, Orientation, OrientationConfiguration } from './guided-tour.constants';
import { AccountService } from 'app/core';
import { TextTourStep, TourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { filter } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<GuidedTourSetting[]>;

@Injectable({ providedIn: 'root' })
export class GuidedTourService {
    public resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';

    public guidedTourSettings: GuidedTourSetting[];
    public currentTour: GuidedTour | null;
    private guidedTourCurrentStepSubject = new Subject<TourStep | null>();
    private guidedTourAvailability = new Subject<boolean>();
    private currentTourStepIndex = 0;
    private onResizeMessage = false;

    constructor(
        private errorHandler: ErrorHandler,
        private http: HttpClient,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private router: Router,
    ) {}

    /**
     * Init method for guided tour settings to retrieve the guided tour settings and subscribe to window resize events
     */
    public init() {
        // Retrieve the guided tour setting from the account service
        this.accountService.identity().then(user => {
            this.guidedTourSettings = user ? user.guidedTourSettings : [];
        });

        // Reset guided tour availability on router navigation
        this.router.events.subscribe(event => {
            if (event instanceof NavigationStart) {
                this.guidedTourAvailability.next(false);
            }
        });

        /**
         * Subscribe to window resize events
         */
        fromEvent(window, 'resize')
            .pipe(debounceTime(200))
            .subscribe(() => {
                if (this.currentTour && this.currentTourStepIndex > 0) {
                    if (this.currentTour.minimumScreenSize && this.currentTour.minimumScreenSize >= window.innerWidth) {
                        this.onResizeMessage = true;
                        this.guidedTourCurrentStepSubject.next(
                            new TextTourStep({
                                headlineTranslateKey: 'tour.resize.headline',
                                contentTranslateKey: 'tour.resize.content',
                            }),
                        );
                    } else {
                        this.onResizeMessage = false;
                        this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex));
                    }
                }
            });
    }

    /**
     * @return current tour step as Observable
     */
    public getGuidedTourCurrentStepStream(): Observable<TourStep | null> {
        return this.guidedTourCurrentStepSubject.asObservable();
    }

    /**
     * @return Observable(true) if the guided tour is available for the current component, otherwise Observable(false)
     */
    public getGuidedTourAvailabilityStream(): Observable<boolean> {
        return this.guidedTourAvailability.asObservable();
    }

    /**
     * Check if the provided tour step is the currently active one
     * @param tourStep: current tour step of the guided tour
     */
    public isCurrentStep(tourStep: TourStep): boolean {
        if (this.currentTour && this.currentTour.steps) {
            return this.currentTourStepDisplay === this.currentTour.steps.indexOf(tourStep) + 1;
        }
        return false;
    }

    /**
     * Navigate to previous tour step
     */
    public backStep(): void {
        if (!this.currentTour) {
            return;
        }

        const currentStep = this.currentTour.steps[this.currentTourStepIndex];
        const previousStep = this.currentTour.steps[this.currentTourStepIndex - 1];
        if (currentStep.closeAction) {
            currentStep.closeAction();
        }
        if (previousStep) {
            this.currentTourStepIndex--;
            if (previousStep.action) {
                previousStep.action();
            }
            setTimeout(() => {
                if (this.checkSelectorValidity()) {
                    this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex));
                } else {
                    this.backStep();
                }
            });
        } else {
            this.resetTour();
        }
    }

    /**
     * Navigate to next tour step
     */
    public nextStep(): void {
        if (!this.currentTour) {
            return;
        }
        const currentStep = this.currentTour.steps[this.currentTourStepIndex];
        const nextStep = this.currentTour.steps[this.currentTourStepIndex + 1];
        if (currentStep.closeAction) {
            currentStep.closeAction();
        }
        if (nextStep) {
            this.currentTourStepIndex++;
            if (nextStep.action) {
                nextStep.action();
            }
            // Usually an action is opening something so we need to give it time to render.
            setTimeout(() => {
                if (this.checkSelectorValidity()) {
                    this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex));
                } else {
                    this.nextStep();
                }
            });
        } else {
            this.finishGuidedTour();
        }
    }

    /**
     * Trigger callback method if there is one and finish the current guided tour by updating the guided tour settings in the database
     * and calling the reset tour method to remove current tour elements
     *
     */
    private finishGuidedTour() {
        if (!this.currentTour) {
            return;
        }
        if (this.currentTour.completeCallback) {
            this.currentTour.completeCallback();
        }
        this.subscribeToAndUpdateGuidedTourSettings(GuidedTourState.FINISHED);
    }

    /**
     * Skip current guided tour after updating the guided tour settings in the database and calling the reset tour method to remove current tour elements.
     */
    public skipTour(): void {
        if (this.currentTour) {
            if (this.currentTour.skipCallback) {
                this.currentTour.skipCallback(this.currentTourStepIndex);
            }
            this.subscribeToAndUpdateGuidedTourSettings(GuidedTourState.STARTED);
        }
    }

    /**
     * Subscribe to the update method call
     * @param guidedTourState GuidedTourState.FINISHED if the tour is closed on the last step, otherwise GuidedTourState.STARTED
     */
    public subscribeToAndUpdateGuidedTourSettings(guidedTourState: GuidedTourState) {
        if (!this.currentTour) {
            return;
        }

        this.updateGuidedTourSettings(this.currentTour.settingsKey, this.currentTourStepDisplay, guidedTourState)
            .pipe(filter(guidedTourSettings => !!guidedTourSettings.body))
            .subscribe(guidedTourSettings => {
                this.guidedTourSettings = guidedTourSettings.body!;
            });

        this.resetTour();
    }

    /**
     * Close tour by resetting `currentTour`, `currentTourStepIndex` and `guidedTourCurrentStepSubject`
     * and remove overlay
     */
    public resetTour(): void {
        document.body.classList.remove('tour-open');
        this.currentTourStepIndex = 0;
        this.guidedTourCurrentStepSubject.next(null);
    }

    /**
     * Start guided tour for given guided tour
     * @param tour: guided tour
     */
    public startTour(): void {
        if (!this.currentTour) {
            return;
        }

        // Filter tour steps according to permissions
        this.currentTour.steps = this.currentTour.steps.filter(step => !step.skipStep || !step.permission || this.accountService.hasAnyAuthorityDirect(step.permission));
        this.currentTourStepIndex = 0;

        // Proceed with tour if it has tour steps and the tour display is allowed for current window size
        if (this.currentTour.steps.length > 0 && this.tourAllowedForWindowSize()) {
            const currentStep = this.currentTour.steps[this.currentTourStepIndex];
            if (currentStep.action) {
                currentStep.action();
            }
            if (this.checkSelectorValidity()) {
                this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex));
            } else {
                this.nextStep();
            }
        }
    }

    /**
     * Checks if the current window size is supposed display the guided tour
     * @return true if the minimum screen size is not defined or greater than the current window.innerWidth, otherwise false
     */
    private tourAllowedForWindowSize(): boolean {
        if (this.currentTour) {
            return !this.currentTour.minimumScreenSize || window.innerWidth >= this.currentTour!.minimumScreenSize;
        }
        return false;
    }

    /**
     *  @return true if highlighted element is available, otherwise false
     */
    private checkSelectorValidity(): boolean {
        if (!this.currentTour) {
            return false;
        }
        if (this.currentTour.steps[this.currentTourStepIndex].selector) {
            const selectedElement = document.querySelector(this.currentTour.steps[this.currentTourStepIndex].selector!);
            if (!selectedElement) {
                this.errorHandler.handleError(
                    // If error handler is configured this should not block the browser.
                    console.warn(
                        `Error finding selector ${this.currentTour.steps[this.currentTourStepIndex].selector} on step ${this.currentTourStepIndex + 1} during guided tour: ${
                            this.currentTour.settingsKey
                        }`,
                    ),
                );
                return false;
            }
        }
        return true;
    }

    /**
     * @return {boolean} if the current step is the last tour step
     */
    public get isOnLastStep(): boolean {
        if (!this.currentTour) {
            return false;
        }
        return this.currentTour.steps.length - 1 === this.currentTourStepIndex;
    }

    /**
     * @return {boolean} if the current step is the first tour step
     */
    public get isOnFirstStep(): boolean {
        return this.currentTourStepIndex === 0;
    }

    /**
     * @return true if the `show resize` message should be displayed, otherwise false
     */
    public get isOnResizeMessage(): boolean {
        return this.onResizeMessage;
    }

    /**
     * @return current tour step number
     */
    public get currentTourStepDisplay(): number {
        return this.currentTourStepIndex + 1;
    }

    /**
     *  @return total count of tour steps of the current tour
     */
    public get currentTourStepCount(): any {
        return this.currentTour && this.currentTour.steps ? this.currentTour.steps.length : 0;
    }

    /**
     *  Prevents the tour from advancing by clicking the backdrop
     *  @return the `preventBackdropFromAdvancing` configuration if tour should advance when clicking on the backdrop
     *  or false if this configuration is not set
     */
    public get preventBackdropFromAdvancing(): boolean {
        if (this.currentTour) {
            return this.currentTour && (this.currentTour.preventBackdropFromAdvancing ? this.currentTour.preventBackdropFromAdvancing : true);
        }
        return false;
    }

    /**
     * Get the tour step with defined orientation
     * @param index current tour step index
     * @return prepared current tour step or null
     */
    private getPreparedTourStep(index: number): TourStep | null {
        if (this.currentTour) {
            return this.setTourOrientation(this.currentTour.steps[index]);
        } else {
            return null;
        }
    }

    /**
     * Set orientation of the passed on tour step
     * @param step passed on tour step of a guided tour
     * @return guided tour step with defined orientation
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

            let currentOrientation: Orientation = Orientation.TOP;
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
     * Send a PUT request to update the guided tour settings of the current user
     * @param guidedTourKey the guided_tour_key that will be stored in the database
     * @param guidedTourStep the last tour step the user visited before finishing / skipping the tour
     * @param guidedTourState displays whether the user has finished (FINISHED) the current tour or only STARTED it and cancelled it in the middle
     * @return Observable<EntityResponseType>: updated guided tour settings
     */
    private updateGuidedTourSettings(guidedTourKey: string, guidedTourStep: number, guidedTourState: GuidedTourState): Observable<EntityResponseType> {
        if (!this.guidedTourSettings) {
            this.resetTour();
            throw new Error('Cannot update non existing guided tour settings');
        }
        const existingSettingIndex = this.guidedTourSettings.findIndex(setting => setting.guidedTourKey === guidedTourKey);
        if (existingSettingIndex !== -1) {
            this.guidedTourSettings[existingSettingIndex].guidedTourStep = guidedTourStep;
            this.guidedTourSettings[existingSettingIndex].guidedTourState = guidedTourState;
        } else {
            this.guidedTourSettings.push(new GuidedTourSetting(guidedTourKey, guidedTourStep, guidedTourState));
        }
        return this.http.put<GuidedTourSetting[]>(this.resourceUrl, this.guidedTourSettings, { observe: 'response' });
    }

    /**
     * Enable a given tour for the component that calls this method and make the start tour button in the navigation bar availability
     * by setting the guidedTourAvailability to true
     *
     * @param guidedTour
     */
    public enableTour(guidedTour: GuidedTour) {
        /**
         * Set timeout so that the reset of the previous guided tour on the navigation end can be processed first
         * to prevent ExpressionChangedAfterItHasBeenCheckedError
         */
        setTimeout(() => {
            this.currentTour = cloneDeep(guidedTour);
            this.guidedTourAvailability.next(true);
        });
    }
}
