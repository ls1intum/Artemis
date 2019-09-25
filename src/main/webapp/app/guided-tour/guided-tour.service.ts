import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { NavigationStart, Router } from '@angular/router';
import { cloneDeep } from 'lodash';
import { JhiAlertService } from 'ng-jhipster';
import { from, fromEvent, Observable, Subject } from 'rxjs';
import { debounceTime } from 'rxjs/internal/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';
import { GuidedTourState, Orientation, OrientationConfiguration, UserInteractionEvent } from './guided-tour.constants';
import { AccountService } from 'app/core';
import { TextTourStep, TourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { filter, take } from 'rxjs/operators';
import { DeviceDetectorService } from 'ngx-device-detector';
import { Course } from 'app/entities/course';
import { Exercise } from 'app/entities/exercise';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';
import { cancelTour } from 'app/guided-tour/tours/general-tour';

export type EntityResponseType = HttpResponse<GuidedTourSetting[]>;

@Injectable({ providedIn: 'root' })
export class GuidedTourService {
    public resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';

    public guidedTourSettings: GuidedTourSetting[];
    public currentTour: GuidedTour | null;
    private guidedTourCurrentStepSubject = new Subject<TourStep | null>();
    private guidedTourAvailability = new Subject<boolean>();
    private isUserInteractionFinished = new Subject<boolean>();
    private currentTourStepIndex = 0;
    private onResizeMessage = false;
    private availableTourForComponent: GuidedTour | null;

    constructor(
        private http: HttpClient,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private router: Router,
        private deviceService: DeviceDetectorService,
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
                this.finishGuidedTour();
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
                        this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex, false));
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
        // The guided tour is currently disabled for mobile devices and tablets
        // TODO optimize guided tour layout for mobile devices and tablets
        return this.guidedTourAvailability.map(isTourAvailable => isTourAvailable && this.deviceService.isDesktop());
    }

    /**
     * @return Observable(true) if the required user interaction for the guided tour step has been executed, otherwise Observable(false)
     */
    public userInteractionFinishedState(): Observable<boolean> {
        return this.isUserInteractionFinished.asObservable();
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
                this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex, this.checkSelectorValidity()));
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
                this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex, this.checkSelectorValidity()));
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
        }
        this.subscribeToAndUpdateGuidedTourSettings(GuidedTourState.STARTED);
        this.showCancelHint();
    }

    /**
     * Show the cancel hint every time a user skips a tour
     */
    private showCancelHint(): void {
        clickOnElement('#account-menu');
        setTimeout(() => {
            this.currentTour = cloneDeep(cancelTour);
            // Proceed with tour if it has tour steps and the tour display is allowed for current window size
            if (this.currentTour.steps.length > 0 && this.tourAllowedForWindowSize()) {
                const currentStep = this.currentTour.steps[this.currentTourStepIndex];
                if (currentStep.action) {
                    currentStep.action();
                }
                this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex, this.checkSelectorValidity()));
            }
        });
    }

    /**
     * Subscribe to the update method call
     * @param guidedTourState GuidedTourState.FINISHED if the tour is closed on the last step, otherwise GuidedTourState.STARTED
     */
    public subscribeToAndUpdateGuidedTourSettings(guidedTourState: GuidedTourState) {
        if (!this.currentTour) {
            return;
        }
        // If the tour was already finished, then keep the state
        const updatedTourState = this.checkTourStateFinished(this.currentTour) ? GuidedTourState.FINISHED : guidedTourState;
        this.updateGuidedTourSettings(this.currentTour.settingsKey, this.currentTourStepDisplay, updatedTourState)
            .pipe(filter(guidedTourSettings => !!guidedTourSettings.body))
            .subscribe(guidedTourSettings => {
                this.guidedTourSettings = guidedTourSettings.body!;
            });

        this.resetTour();
    }

    /**
     * Check if the current user has already finished a given guided tour by filtering the user's guided tour settings and comp
     * @param guidedTour that should be checked for the finished state
     */
    public checkTourStateFinished(guidedTour: GuidedTour): boolean {
        const tourSetting = this.guidedTourSettings.filter(setting => setting.guidedTourKey === guidedTour.settingsKey);
        return !!(tourSetting.length > 0 && tourSetting[0].guidedTourState.toString() === GuidedTourState[GuidedTourState.FINISHED]);
    }

    /**
     * Close tour by resetting `currentTour`, `currentTourStepIndex` and `guidedTourCurrentStepSubject`
     * and remove overlay
     */
    public resetTour(): void {
        document.body.classList.remove('tour-open');
        this.currentTourStepIndex = 0;
        this.currentTour = null;
        this.guidedTourCurrentStepSubject.next(null);
    }

    /**
     * Enable a smooth user interaction
     * @param targetNode an HTMLElement of which DOM changes should be observed
     * @param userInteraction the user interaction to complete the tour step
     */
    public enableUserInteraction(targetNode: HTMLElement, userInteraction: UserInteractionEvent): void {
        this.isUserInteractionFinished.next(false);
        if (!this.currentTour) {
            return;
        }
        const nextStep = this.currentTour.steps[this.currentTourStepIndex + 1];

        switch (userInteraction) {
            case UserInteractionEvent.WAIT_FOR_SELECTOR: {
                if (nextStep && nextStep.highlightSelector) {
                    this.waitForElement(nextStep.highlightSelector);
                } else {
                    this.enableNextStepClick();
                }
                break;
            }
            case UserInteractionEvent.CLICK: {
                from(this.observeDomMutations(targetNode, userInteraction))
                    .pipe(take(1))
                    .subscribe(() => {
                        this.enableNextStepClick();
                    });
                break;
            }
            case UserInteractionEvent.ACE_EDITOR: {
                from(this.observeDomMutations(targetNode, userInteraction)).subscribe((mutations: MutationRecord[]) => {
                    mutations.forEach(() => {
                        this.enableNextStepClick();
                    });
                });
                break;
            }
        }
    }

    public observeDomMutations(targetNode: HTMLElement, userInteraction: UserInteractionEvent) {
        return new Promise(resolve => {
            const observer = new MutationObserver(mutations => {
                switch (userInteraction) {
                    case UserInteractionEvent.CLICK: {
                        observer.disconnect();
                        resolve(mutations);
                        break;
                    }
                    case UserInteractionEvent.ACE_EDITOR: {
                        mutations.forEach(mutation => {
                            if (mutation.addedNodes.length !== mutation.removedNodes.length && (mutation.addedNodes.length >= 1 || mutation.removedNodes.length >= 1)) {
                                observer.disconnect();
                                resolve(mutations);
                            }
                        });
                        break;
                    }
                    default: {
                        observer.disconnect();
                        resolve(mutations);
                    }
                }
            });
            observer.observe(targetNode, {
                attributes: true,
                childList: true,
                characterData: true,
            });
        });
    }

    /**
     * Wait for the next step selector to appear in the DOM and continue with the next step
     * @param nextStepSelector the selector string of the next element that should appear in the DOM
     */
    private waitForElement(nextStepSelector: string) {
        const interval = setInterval(() => {
            const nextElement = document.querySelector(nextStepSelector);
            if (nextElement) {
                clearInterval(interval);
                this.enableNextStepClick();
            }
        }, 1000);
    }

    /**
     * Remove the disabled attribute so that the next button is clickable again
     */
    private enableNextStepClick() {
        this.isUserInteractionFinished.next(true);
        const nextButton = document.querySelector('.next-button');
        if (nextButton && nextButton.attributes.getNamedItem('disabled')) {
            nextButton.attributes.removeNamedItem('disabled');
        }
    }

    /**
     * Start guided tour for given guided tour
     */
    public startTour(): void {
        if (!this.availableTourForComponent) {
            return;
        }
        // Keep current tour null until start tour is triggered, else it could be somehow accessed through nextStep() calls
        this.currentTour = this.availableTourForComponent;

        // Filter tour steps according to permissions
        this.currentTour.steps = this.currentTour.steps.filter(step => !step.skipStep && (!step.permission || this.accountService.hasAnyAuthorityDirect(step.permission)));
        this.currentTourStepIndex = 0;

        // Proceed with tour if it has tour steps and the tour display is allowed for current window size
        if (this.currentTour.steps.length > 0 && this.tourAllowedForWindowSize()) {
            const currentStep = this.currentTour.steps[this.currentTourStepIndex];
            if (currentStep.action) {
                currentStep.action();
            }
            this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex, this.checkSelectorValidity()));
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
    public checkSelectorValidity(): boolean {
        if (!this.currentTour) {
            return false;
        }
        const selector = this.currentTour.steps[this.currentTourStepIndex].highlightSelector;
        if (selector) {
            const selectedElement = document.querySelector(selector);
            if (!selectedElement) {
                console.warn(
                    `Error finding selector ${this.currentTour.steps[this.currentTourStepIndex].highlightSelector} on step ${this.currentTourStepIndex + 1} during guided tour: ${
                        this.currentTour.settingsKey
                    }`,
                );
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if the current step is the last tour step, otherwise false
     */
    public get isOnLastStep(): boolean {
        if (!this.currentTour) {
            return false;
        }
        return this.currentTour.steps.length - 1 === this.currentTourStepIndex;
    }

    /**
     * @return true if the current step is the first tour step, otherwise false
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
     * @param selectorAvailable true if the current tour step selector is valid, otherwise false
     * @return prepared current tour step or null
     */
    private getPreparedTourStep(index: number, selectorAvailable: boolean): TourStep | null {
        if (!this.currentTour) {
            return null;
        }
        return selectorAvailable ? this.setTourOrientation(this.currentTour.steps[index]) : this.setStepAlreadyFinishedHint();
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

    private setStepAlreadyFinishedHint(): TourStep {
        return new TextTourStep({
            headlineTranslateKey: 'tour.stepAlreadyExecuted.headline',
            contentTranslateKey: 'tour.stepAlreadyExecuted.content',
            hintTranslateKey: 'tour.stepAlreadyExecuted.hint',
        });
    }

    /**
     * Send a PUT request to update the guided tour settings of the current user
     * @param guidedTourKey the guided_tour_key that will be stored in the database
     * @param guidedTourStep the last tour step the user visited before finishing / skipping the tour
     * @param guidedTourState displays whether the user has finished (FINISHED) the current tour or only STARTED it and cancelled it in the middle
     * @return Observable<EntityResponseType>: updated guided tour settings
     */
    public updateGuidedTourSettings(guidedTourKey: string, guidedTourStep: number, guidedTourState: GuidedTourState): Observable<EntityResponseType> {
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
            this.availableTourForComponent = this.currentTour;
            this.guidedTourAvailability.next(true);
            if (!this.checkTourStateFinished(guidedTour)) {
                this.startTour();
            }
        }, 500);
    }

    /**
     * Check if the course and exercise for the tour are available on the course-exercise component
     * @param course for which the guided tour availability should be checked
     * @param guidedTour that should be enabled
     */
    public enableTourForCourseExerciseComponent(course: Course | null, guidedTour: GuidedTour) {
        if (!guidedTour.exerciseTitle || !course || !course.exercises) {
            return;
        }
        const hasExerciseForGuidedTour = course.exercises.find(exercise => exercise.title === guidedTour.exerciseTitle);
        if (hasExerciseForGuidedTour) {
            this.enableTour(guidedTour);
        }
    }

    /**
     * Check if the course list contains the course for which the tour is available
     * @param courses which can contain the needed course for the tour
     * @param guidedTour that should be enabled
     */
    public enableTourForCourseOverview(courses: Course[], guidedTour: GuidedTour) {
        if (courses.find(course => course.title === guidedTour.courseTitle)) {
            this.enableTour(guidedTour);
        }
    }

    /**
     * Check if the exercise list contains the exercise for which the tour is available
     * @param exercise which can contain the needed exercise for the tour
     * @param guidedTour that should be enabled
     */
    public enableTourForExercise(exercise: Exercise, guidedTour: GuidedTour) {
        if (exercise.title === guidedTour.exerciseTitle) {
            this.enableTour(guidedTour);
        }
    }
}
