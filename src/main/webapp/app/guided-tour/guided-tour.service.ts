import { Injectable } from '@angular/core';
import isMobile from 'ismobilejs-es5';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { cloneDeep } from 'lodash-es';
import { AlertService } from 'app/core/util/alert.service';
import { BehaviorSubject, Observable, Subject, fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map, mergeMap, switchMap } from 'rxjs/operators';
import { GuidedTourMapping, GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';
import { GuidedTourState, Orientation, OrientationConfiguration, ResetParticipation, UserInteractionEvent } from './guided-tour.constants';
import { User } from 'app/core/user/user.model';
import { TextTourStep, TourStep, UserInterActionTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { checkPageUrlEnding, clickOnElement, determineUrlMatching, getUrlParams } from 'app/guided-tour/guided-tour.utils';
import { cancelTour, completedTour } from 'app/guided-tour/tours/general-tour';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AssessmentObject } from './guided-tour-task.model';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { CourseManagementService } from '../course/manage/course-management.service';

export type EntityResponseType = HttpResponse<GuidedTourSetting[]>;

@Injectable({ providedIn: 'root' })
export class GuidedTourService {
    public resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
    public guidedTourSettings: GuidedTourSetting[];
    public currentTour?: GuidedTour;

    /** Helper variables */
    public restartIsLoading = false;
    public currentTourStepIndex = 0;
    private availableTourForComponent?: GuidedTour;
    private onResizeMessage = false;
    private modelingResultCorrect = false;
    private assessmentObject = new AssessmentObject(0, 0);

    /** Guided tour course and exercise mapping */
    public guidedTourMapping?: GuidedTourMapping;

    /** Helper variables for multi-page tours */
    public currentDotSubject = new Subject<number>();
    public nextDotSubject = new Subject<number>();
    private pageUrls = new Map<string, string>();

    /** Current course and exercise */
    private currentCourse?: Course;
    private currentExercise?: Exercise;

    /** Guided tour service subjects */
    private guidedTourCurrentStepSubject = new Subject<TourStep | undefined>();
    private guidedTourAvailabilitySubject = new Subject<boolean>();
    private isUserInteractionFinishedSubject = new Subject<boolean>();
    private checkModelingComponentSubject = new Subject<string | undefined>();
    public isBackPageNavigation = new BehaviorSubject<boolean>(false);
    private isComponentPageLoaded = new Subject<boolean>();

    private isMobile = false;

    constructor(
        private http: HttpClient,
        private alertService: AlertService,
        private accountService: AccountService,
        private router: Router,
        private profileService: ProfileService,
        private participationService: ParticipationService,
        private tutorParticipationService: TutorParticipationService,
        private courseService: CourseManagementService,
    ) {}

    /**
     * Init method for guided tour settings to retrieve the guided tour settings and subscribe to window resize events
     */
    public init() {
        this.isMobile = isMobile(window.navigator.userAgent).any;
        // Retrieve the guided tour setting from the account service after the user is logged in
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                this.guidedTourSettings = user.guidedTourSettings ? user.guidedTourSettings : [];
            }
        });

        this.isComponentPageLoaded.subscribe((isLoaded) => {
            setTimeout(() => {
                if (isLoaded) {
                    this.resetUserInteractionFinishedState(this.currentStep);
                    this.setPreparedTourStep();
                }
            }, 1200);
        });

        // Retrieve the guided tour mapping from the profile service
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo && profileInfo.guidedTourMapping) {
                this.guidedTourMapping = profileInfo.guidedTourMapping;
            }
        });

        // Reset guided tour availability on router navigation
        this.router.events.subscribe((event) => {
            // Reset currentExercise and currentCourse for every page
            if (event instanceof NavigationStart) {
                this.currentExercise = undefined;
                this.currentCourse = undefined;
            }

            // Checks the guided tour availability on router navigation during an active tutorial
            if (event instanceof NavigationEnd) {
                if (this.availableTourForComponent && this.currentTour) {
                    this.guidedTourCurrentStepSubject.next(undefined);
                    this.assessmentObject = new AssessmentObject(0, 0);
                    this.checkNextTourStepOnNavigation();
                } else {
                    this.skipTour();
                    this.guidedTourAvailabilitySubject.next(false);
                }
            }
        });

        /**
         * Subscribe to window resize events
         */
        fromEvent(window, 'resize')
            .pipe(debounceTime(200))
            .subscribe(() => {
                // TODO: we should use responsive design here instead of checking for mobile/desktop
                if (this.currentTour && !this.isMobile) {
                    // Show resize tour step if the window size falls below the defined minimum tour size, except for VideoTourSteps
                    if (this.tourMinimumScreenSize >= window.innerWidth && !(this.currentTour.steps[this.currentTourStepIndex] instanceof VideoTourStep)) {
                        this.onResizeMessage = true;
                        this.guidedTourCurrentStepSubject.next(new TextTourStep({ headlineTranslateKey: 'tour.resize.headline', contentTranslateKey: 'tour.resize.content' }));
                    } else {
                        if (this.onResizeMessage) {
                            this.onResizeMessage = false;
                            this.setPreparedTourStep();
                        }
                    }
                }
            });
    }

    /**
     * Checks and prepares the next tour step on navigation for multi-page tours
     */
    private checkNextTourStepOnNavigation() {
        if (!this.currentTour) {
            return;
        }
        const currentStep = this.currentTour.steps[this.currentTourStepIndex] as UserInterActionTourStep;
        const nextStep = this.currentTour.steps[this.currentTourStepIndex + 1];

        // Prepares previous tour step for backward navigation
        if (this.isBackPageNavigation.value) {
            this.resetUserInteractionFinishedState(currentStep);
        } else {
            // Prepares next tour step
            if (currentStep && currentStep.userInteractionEvent && currentStep.userInteractionEvent === UserInteractionEvent.CLICK && nextStep && nextStep.pageUrl) {
                if (determineUrlMatching(this.router.url, nextStep.pageUrl)) {
                    this.currentDotSubject.next(this.currentTourStepIndex);
                    this.nextDotSubject.next(this.currentTourStepIndex + 1);
                    this.currentTourStepIndex += 1;
                    this.pageUrls.set(nextStep.pageUrl, this.router.url);
                } else if (this.currentTour) {
                    // Ends guided tour if the navigation is done through a multi-page tutorial
                    this.guidedTourAvailabilitySubject.next(false);
                    this.skipTour();
                }
            }
        }
    }

    /**
     * @return defined minimum screen size number
     */
    private get tourMinimumScreenSize(): number {
        return this.currentTour && this.currentTour.minimumScreenSize ? this.currentTour.minimumScreenSize : 1000;
    }

    /**
     * @return current tour step as Observable
     */
    public getGuidedTourCurrentStepStream(): Observable<TourStep | undefined> {
        return this.guidedTourCurrentStepSubject.asObservable();
    }

    /**
     * @return Observable(true) if the guided tour is available for the current component, otherwise Observable(false)
     */
    public getGuidedTourAvailabilityStream(): Observable<boolean> {
        // The guided tour is currently disabled for mobile devices and tablets
        // TODO optimize guided tour layout for mobile devices and tablets
        // TODO: we should use responsive design here instead of checking for mobile/desktop
        return this.guidedTourAvailabilitySubject.pipe(map((isTourAvailable) => isTourAvailable && !this.isMobile));
    }

    /**
     * @return Observable(true) if the required user interaction for the guided tour step has been executed, otherwise Observable(false)
     */
    public userInteractionFinishedState(): Observable<boolean> {
        return this.isUserInteractionFinishedSubject.asObservable();
    }

    /**
     * @return Observable of the current modeling task UML name
     */
    public checkModelingComponent(): Observable<string | undefined> {
        return this.checkModelingComponentSubject.asObservable();
    }

    /**
     * Updates the modelingResultCorrect variable on whether the implemented UML model is correct and enables
     * the next step button
     * @param umlName   name of the UML element for the modeling task
     * @param result    true if the UML element has been modeled correctly, otherwise false
     */
    public updateModelingResult(umlName: string, result: boolean) {
        if (!this.currentStep || !this.currentStep.modelingTask) {
            return;
        }
        if (result && this.currentStep.modelingTask.umlName === umlName) {
            this.modelingResultCorrect = result;
            setTimeout(() => {
                this.enableNextStepClick();
                this.checkModelingComponentSubject.next(undefined);
            }, 0);
        }
    }

    /**
     * Helper method for multi-page tutorials that notifies the guided-tour service when the current component has
     * been fully loaded
     */
    public componentPageLoaded() {
        if (!this.currentTour) {
            return;
        }
        this.isComponentPageLoaded.next(true);
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
     * Determines if the given tour is the current tour
     * @param guidedTour that is checked
     */
    public isCurrentTour(guidedTour: GuidedTour) {
        if (this.currentTour && this.currentTour.steps) {
            return this.currentTour.settingsKey === guidedTour.settingsKey;
        }
        return false;
    }

    /**
     * Check if the provided tour step is the currently active one
     */
    private get currentStep(): any | undefined {
        if (!this.currentTour || !this.currentTour.steps) {
            return undefined;
        }
        return this.currentTour.steps[this.currentTourStepIndex];
    }

    /**
     * Get the current step string for the headline, that shows which step is currently displayed, `currentStep / totalStep`
     */
    public getCurrentStepString() {
        if (!this.currentTour) {
            return;
        }
        const currentStep = this.currentTourStepIndex + 1;
        const totalSteps = this.currentTour.steps.length;
        return `${currentStep} / ${totalSteps}`;
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
        this.currentDotSubject.next(this.currentTourStepIndex);
        this.nextDotSubject.next(this.currentTourStepIndex - 1);

        if (currentStep?.closeAction) {
            currentStep.closeAction();
        }

        const previousStepLocation = this.determinePreviousStepLocation();
        if (currentStep.pageUrl && previousStepLocation !== this.router.url) {
            this.isBackPageNavigation.next(true);
            this.router.navigateByUrl(this.determinePreviousStepLocation()).then();
        }

        if (previousStep) {
            this.currentTourStepIndex--;
            if (previousStep.action) {
                previousStep.action();
            }
            // Usually an action is opening something so we need to give it time to render.
            setTimeout(() => {
                if (!this.isBackPageNavigation.value) {
                    this.resetUserInteractionFinishedState(previousStep);
                    this.setPreparedTourStep();
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
        const timeout = currentStep instanceof UserInterActionTourStep && currentStep.userInteractionEvent === UserInteractionEvent.CLICK && this.checkSelectorValidity() ? 600 : 0;
        this.currentDotSubject.next(this.currentTourStepIndex);
        this.nextDotSubject.next(this.currentTourStepIndex + 1);
        this.isBackPageNavigation.next(false);

        if (currentStep?.closeAction) {
            currentStep.closeAction();
        }
        if (nextStep) {
            this.currentTourStepIndex++;
            if (nextStep.action) {
                nextStep.action();
            }
            // Usually an action is opening something so we need to give it time to render.
            setTimeout(() => {
                this.resetUserInteractionFinishedState(nextStep);
                this.setPreparedTourStep();
            }, timeout);
        } else {
            this.finishGuidedTour();
        }
    }

    /**
     * Resets the user interaction finished state for given tour step
     * @param tourStep  if the tour step is an instance of UserInterActionTourStep, the user interaction finished state
     * will be set to false
     */
    private resetUserInteractionFinishedState(tourStep: TourStep) {
        if (tourStep instanceof UserInterActionTourStep) {
            this.isUserInteractionFinishedSubject.next(false);
        }
    }

    /**
     * Trigger callback method if there is one and finish the current guided tour by updating the guided tour settings in the database
     * and calling the reset tour method to remove current tour elements
     */
    public finishGuidedTour() {
        if (!this.currentTour) {
            return;
        }

        if (this.currentTour.completeCallback) {
            this.currentTour.completeCallback();
        }

        if (this.isCurrentTour(completedTour)) {
            this.resetTour();
            return;
        }

        const nextStep = this.currentTour.steps[this.currentTourStepIndex + 1];
        if (!nextStep) {
            this.subscribeToAndUpdateGuidedTourSettings(GuidedTourState.FINISHED);
            this.showCompletedTourStep();
        } else {
            this.subscribeToAndUpdateGuidedTourSettings(GuidedTourState.STARTED);
        }
    }

    /**
     * Skip current guided tour after updating the guided tour settings in the database and calling the reset tour method to remove current tour elements.
     */
    public skipTour(): void {
        if (!this.currentTour) {
            return;
        }

        if (this.currentTour.skipCallback) {
            this.currentTour.skipCallback(this.currentTourStepIndex);
        }

        if (this.currentTour === cancelTour || this.currentTour === completedTour) {
            this.resetTour();
            return;
        }

        if (this.currentTourStepIndex + 1 === this.getFilteredTourSteps().length) {
            this.finishGuidedTour();
        } else {
            this.subscribeToAndUpdateGuidedTourSettings(GuidedTourState.STARTED);
            this.showCancelHint();
        }
    }

    /**
     * Show the cancel hint the first time a user skips a tour
     */
    private showCancelHint(): void {
        /** Do not show hint if the user has seen it already */
        const hasStartedOrFinishedTour = this.checkTourState(cancelTour);
        if (hasStartedOrFinishedTour) {
            return;
        }

        clickOnElement('#account-menu[aria-expanded="false"]');
        setTimeout(() => {
            this.currentTour = cloneDeep(cancelTour);
            /** Proceed with tour if the tour has tour steps and the tour display is allowed for current window size */
            if (this.currentTour.steps.length > 0 && this.tourAllowedForWindowSize()) {
                const currentStep = this.currentTour.steps[this.currentTourStepIndex];
                if (currentStep.action) {
                    currentStep.action();
                }
                this.setPreparedTourStep();
            }
        });
    }

    /**
     * Show the completed tour step every time a user completes a tour
     */
    private showCompletedTourStep(): void {
        setTimeout(() => {
            this.currentTour = cloneDeep(completedTour);
            /** Proceed with tour if the tour has tour steps and the tour display is allowed for current window size */
            if (this.currentTour.steps.length > 0 && this.tourAllowedForWindowSize()) {
                this.setPreparedTourStep();
            }
        });
    }

    /**
     * Subscribe to the update method call
     * @param guidedTourState GuidedTourState.FINISHED if the tour is closed on the last step, otherwise GuidedTourState.STARTED
     */
    public subscribeToAndUpdateGuidedTourSettings(guidedTourState: GuidedTourState) {
        if (!this.currentTour || this.isCurrentTour(completedTour)) {
            this.resetTour();
            return;
        }
        // If the tour was already finished, then keep the state
        const updatedTourState = this.checkTourState(this.currentTour, GuidedTourState.FINISHED) ? GuidedTourState.FINISHED : guidedTourState;
        this.updateGuidedTourSettings(this.currentTour.settingsKey, this.currentTourStepDisplay, updatedTourState)
            .pipe(filter((guidedTourSettings) => !!guidedTourSettings.body))
            .subscribe((guidedTourSettings) => {
                this.guidedTourSettings = guidedTourSettings.body!;
            });
        this.resetTour();
    }

    /**
     * Check if the current user has already finished a given guided tour by filtering the user's guided tour settings and comp
     * @param guidedTour that should be checked for the state
     * @param state that should be checked, if no state is given, then true is returned if the tour has been started or finished
     */
    private checkTourState(guidedTour: GuidedTour, state?: GuidedTourState): boolean {
        const tourSetting = this.guidedTourSettings.filter((setting) => setting.guidedTourKey === guidedTour.settingsKey);
        if (state) {
            return tourSetting.length === 1 && tourSetting[0].guidedTourState.toString() === GuidedTourState[state];
        }
        return tourSetting.length >= 1;
    }

    /**
     * Get the last step that the user visited during the given tour
     */
    public getLastSeenTourStepIndex(): number {
        if (!this.availableTourForComponent || !this.guidedTourSettings) {
            return 0;
        }
        const tourSettings = this.guidedTourSettings.filter((setting) => setting.guidedTourKey === this.availableTourForComponent!.settingsKey);

        if (tourSettings.length !== 0 && this.hasValidTourStepNumber(tourSettings)) {
            return tourSettings[0].guidedTourStep;
        }

        return 0;
    }

    /**
     * Get the last seen tour step when initiating a guided tour
     * This method takes multi page guided tours into account and retrieves the right tour step for the respective component page
     */
    public getLastSeenTourStepForInit(): number {
        if (!this.availableTourForComponent) {
            return 0;
        }

        let lastSeenTourStep = this.isMultiPageTour() ? this.determineTourStepForComponent() : this.getLastSeenTourStepIndex();

        /** If the user has seen the tour already, then set the last seen tour step to -1
         *  to enable the restart of the tour instead of just starting it
         */
        // We need the !== undefined check because in case lastSeenTourStep is 0, the condition will be seen as false
        if (lastSeenTourStep !== undefined) {
            lastSeenTourStep = this.availableTourForComponent.steps[lastSeenTourStep] ? lastSeenTourStep : 0;
            return lastSeenTourStep === 0 ? -1 : lastSeenTourStep;
        } else {
            return 0;
        }
    }

    private isMultiPageTour() {
        if (!this.availableTourForComponent) {
            return false;
        }
        return this.availableTourForComponent.steps.filter((tourStep) => tourStep.pageUrl).length > 0;
    }

    /**
     * Determines if the tour step stored in the database is valid for the current tour
     * It might be that tour steps have been removed in the meantime
     * @param tourSettings  the tour setting that is stored for the current tour
     */
    private hasValidTourStepNumber(tourSettings: GuidedTourSetting[]): boolean {
        return tourSettings[0].guidedTourStep <= this.getFilteredTourSteps().length;
    }

    /**
     * This method determines the right starting tour step for multi-page guided tours
     */
    private determineTourStepForComponent(): number | undefined {
        // Find steps with a pageUrl attribute that matches the current router url
        const stepsForComponent = this.availableTourForComponent!.steps.filter((tourStep) => {
            const match = tourStep.pageUrl ? determineUrlMatching(this.router.url, tourStep.pageUrl) : [];
            if (match && tourStep.pageUrl && checkPageUrlEnding(this.router.url, match[0])) {
                return match;
            }
        });

        if (stepsForComponent) {
            const stepForComponent = stepsForComponent.find((step) => {
                if (step.pageUrl) {
                    // Since we could not include the params in the URL matching we have to do a final check here
                    return getUrlParams(step.pageUrl) === getUrlParams(this.router.url);
                }
            });
            return stepForComponent ? this.availableTourForComponent!.steps.indexOf(stepForComponent) : undefined;
        }
        return undefined;
    }

    /**
     * This is a helper method to determine the previous step location for backward navigation in a multi-page tour
     */
    private determinePreviousStepLocation(): string {
        if (!this.availableTourForComponent) {
            return this.router.url;
        }

        let previousStepLocation = this.router.url;
        const tourStepsWithUrl = this.availableTourForComponent.steps.filter((tourStep, index) => {
            return tourStep.pageUrl && index < this.currentTourStepIndex;
        });

        if (tourStepsWithUrl) {
            const lastTourStepWithUrl = tourStepsWithUrl.last();
            const previousStepLocationKey = lastTourStepWithUrl && lastTourStepWithUrl.pageUrl ? lastTourStepWithUrl.pageUrl : this.router.url;
            if (this.pageUrls.has(previousStepLocationKey)) {
                previousStepLocation = <string>this.pageUrls.get(previousStepLocationKey);
            }
        }
        return previousStepLocation;
    }

    /**
     * Close tour by resetting `currentTour`, `currentTourStepIndex` and `guidedTourCurrentStepSubject`
     * and remove overlay
     */
    public resetTour(): void {
        if (this.isCurrentTour(cancelTour)) {
            this.updateGuidedTourSettings(cancelTour.settingsKey, 1, GuidedTourState.FINISHED);
        }

        document.body.classList.remove('tour-open');
        this.currentTour = undefined;
        this.currentTourStepIndex = 0;
        this.guidedTourCurrentStepSubject.next(undefined);
        this.assessmentObject = new AssessmentObject(0, 0);
    }

    /**
     * Enable a smooth user interaction
     * @param targetNode an HTMLElement of which DOM changes should be observed
     * @param userInteraction the user interaction to complete the tour step
     * @param modelingTask the modeling task identifier
     */
    public enableUserInteraction(targetNode: HTMLElement, userInteraction: UserInteractionEvent, modelingTask?: string): void {
        if (!this.currentTour) {
            return;
        }

        const currentStep = this.currentTour.steps[this.currentTourStepIndex] as UserInterActionTourStep;

        if (userInteraction === UserInteractionEvent.WAIT_FOR_SELECTOR) {
            const nextStep = this.currentTour.steps[this.currentTourStepIndex + 1];
            const afterNextStep = this.currentTour.steps[this.currentTourStepIndex + 2];
            this.handleWaitForSelectorEvent(currentStep, nextStep, afterNextStep);
        } else {
            /** At a minimum one of childList, attributes, and characterData must be true, otherwise, a TypeError exception will be thrown. */
            let options: MutationObserverInit = { attributes: true, childList: true, characterData: true };

            if (userInteraction === UserInteractionEvent.CLICK) {
                targetNode.addEventListener(
                    'click',
                    () => {
                        if (currentStep.triggerNextStep) {
                            this.nextStep();
                        } else {
                            this.enableNextStepClick();
                        }
                    },
                    false,
                );
            } else if (userInteraction === UserInteractionEvent.ACE_EDITOR) {
                /** We observe any added or removed lines in the .ace_text-layer node and trigger enableNextStepClick() */
                targetNode = document.querySelector('.ace_text-layer') as HTMLElement;
                this.observeMutations(targetNode, options)
                    .pipe(
                        filter(
                            (mutation: MutationRecord) =>
                                mutation.addedNodes.length !== mutation.removedNodes.length && (mutation.addedNodes.length >= 1 || mutation.removedNodes.length >= 1),
                        ),
                    )
                    .subscribe(() => {
                        this.enableNextStepClick();
                    });
            } else if (userInteraction === UserInteractionEvent.MODELING) {
                /** We observe any DOM mutation in the .apollon-editor node and its children
                 *  If the UML model is correct then enableNextStepClick() will be called
                 */
                options = { childList: true, subtree: true };
                targetNode = document.querySelector('.modeling-editor .apollon-container .apollon-editor svg') as HTMLElement;

                this.modelingResultCorrect = false;
                this.checkModelingComponentSubject.next(modelingTask);

                this.observeMutations(targetNode, options)
                    .pipe(debounceTime(100), distinctUntilChanged())
                    .subscribe(() => {
                        this.checkModelingComponentSubject.next(modelingTask);
                        if (this.modelingResultCorrect) {
                            this.enableNextStepClick();
                        }
                    });
            } else if (userInteraction === UserInteractionEvent.ASSESS_SUBMISSION) {
                if (this.isAssessmentCorrect()) {
                    this.enableNextStepClick();
                }
            }
        }
    }

    /**
     * Enables the next step click if the highlightSelector of the next step or
     * the highlightSelector of the after next step are visible
     * @param currentStep  current tour step
     * @param nextStep  next tour step
     * @param afterNextStep the tour step after the next tour step
     */
    private handleWaitForSelectorEvent(currentStep: UserInterActionTourStep, nextStep: TourStep | undefined, afterNextStep: TourStep | undefined) {
        if (nextStep && nextStep.highlightSelector) {
            this.waitForElement(nextStep.highlightSelector, afterNextStep?.highlightSelector, currentStep.triggerNextStep);
        } else {
            this.enableNextStepClick();
        }
    }

    /**
     * Handles the mutation observer for the user interactions
     * @param target    target node of an HTMLElement of which DOM changes should be observed
     * @param options   the configuration options for the mutation observer
     */
    public observeMutations = (target: any, options: MutationObserverInit) =>
        new Observable<MutationRecord>((subscribe) => {
            const observer = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    subscribe.next(mutation);
                });
            });
            observer.observe(target, options);
            return () => observer.disconnect();
        });

    /**
     * Wait for the next step selector to appear in the DOM and continue with the next step
     * @param nextStepSelector the selector string of the next element that should appear in the DOM
     * @param afterNextStepSelector if the nextSelector does not show up in the DOM then wait for the step afterwards as well
     * @param triggerNextStep if the next step should be called as soon as the element is found
     */
    private waitForElement(nextStepSelector: string, afterNextStepSelector?: string, triggerNextStep?: boolean) {
        const interval = setInterval(() => {
            const nextElement = document.querySelector(nextStepSelector);
            const afterNextElement = afterNextStepSelector ? document.querySelector(afterNextStepSelector) : undefined;
            if (nextElement || afterNextElement) {
                clearInterval(interval);
                if (triggerNextStep) {
                    this.nextStep();
                } else {
                    this.enableNextStepClick();
                }
            }
        }, 1000);
    }

    /**
     * Remove the disabled attribute so that the next button is clickable again
     */
    private enableNextStepClick() {
        this.isUserInteractionFinishedSubject.next(true);
    }

    /**
     * Start or restart the guided tour based on the last seen tour step
     */
    public initGuidedTour(): void {
        switch (this.getLastSeenTourStepForInit()) {
            case -1: {
                this.restartTour();
                break;
            }
            default: {
                this.startTour();
            }
        }
    }

    /**
     * Start guided tour for given guided tour
     */
    private startTour(): void {
        if (!this.availableTourForComponent) {
            return;
        }
        // Keep current tour undefined until start tour is triggered, else it could be somehow accessed through nextStep() calls
        this.currentTour = this.availableTourForComponent;

        // Filter tour steps according to permissions
        this.currentTour.steps = this.getFilteredTourSteps();
        this.currentTourStepIndex = this.isMultiPageTour() ? this.getLastSeenTourStepForInit() : this.getLastSeenTourStepForInit() - 1;

        // Proceed with tour if it has tour steps and the tour display is allowed for current window size
        if (this.currentTour.steps.length > 0 && this.tourAllowedForWindowSize()) {
            if (!this.currentTour.steps[this.currentTourStepIndex]) {
                // Set current tour step index to 0 if the current tour step cannot be found
                this.currentTourStepIndex = 0;
            }
            const currentStep = this.currentTour.steps[this.currentTourStepIndex];
            if (currentStep.action) {
                currentStep.action();
            }
            if (currentStep.pageUrl) {
                this.pageUrls.set(currentStep.pageUrl, this.router.url);
            }
            this.resetUserInteractionFinishedState(currentStep);
            this.setPreparedTourStep();
        }
    }

    /** Resets participation and enables the restart of the current tour */
    public restartTour() {
        if (this.currentCourse && this.currentExercise && this.availableTourForComponent) {
            switch (this.availableTourForComponent.resetParticipation) {
                // Reset exercise participation
                case ResetParticipation.EXERCISE_PARTICIPATION:
                    this.restartIsLoading = true;
                    const isProgrammingExercise = this.currentExercise.type === ExerciseType.PROGRAMMING;
                    this.participationService
                        .findParticipationForCurrentUser(this.currentExercise.id!)
                        .pipe(
                            map((response: HttpResponse<StudentParticipation>) => response.body!),
                            mergeMap((participation) =>
                                this.participationService.deleteForGuidedTour(participation.id!, {
                                    deleteBuildPlan: isProgrammingExercise,
                                    deleteRepository: isProgrammingExercise,
                                }),
                            ),
                            switchMap(() => this.deleteGuidedTourSetting(this.availableTourForComponent!.settingsKey)),
                        )
                        .subscribe({
                            next: () => {
                                this.navigateToUrlAfterRestart(`/courses/${this.currentCourse!.id}/exercises`);
                            },
                            error: () => {
                                // start tour in case the participation was deleted otherwise
                                this.restartIsLoading = false;
                                this.startTour();
                            },
                        });
                    break;
                // Reset tutor assessment participation
                case ResetParticipation.TUTOR_ASSESSMENT:
                    this.restartIsLoading = true;
                    this.tutorParticipationService.deleteTutorParticipationForGuidedTour(this.currentCourse, this.currentExercise).subscribe({
                        next: () => {
                            this.deleteGuidedTourSetting(this.availableTourForComponent!.settingsKey).subscribe(() => {
                                this.navigateToUrlAfterRestart('/course-management');
                            });
                        },
                        error: () => {
                            this.restartIsLoading = false;
                            this.startTour();
                        },
                    });
                    break;
                case ResetParticipation.NONE:
                    this.startTour();
                    break;
            }
        } else {
            this.startTour();
        }
    }

    /**
     * Navigate to page after resetting a guided tour participation
     */
    private navigateToUrlAfterRestart(url: string) {
        this.router.navigateByUrl(url).then(() => {
            location.reload();
        });

        // Keep loading icon until the page is being refreshed
        window.onload = function () {
            this['restartIsLoading'] = false;
        };
    }

    public getFilteredTourSteps(): TourStep[] {
        if (!this.availableTourForComponent) {
            return [];
        }
        return this.availableTourForComponent.steps.filter((step) => !step.disableStep && (!step.permission || this.accountService.hasAnyAuthorityDirect(step.permission)));
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
        const currentTourStep = this.currentTour.steps[this.currentTourStepIndex];
        if (!currentTourStep) {
            this.resetTour();
            return false;
        }

        return !currentTourStep.highlightSelector || !!document.querySelector(currentTourStep.highlightSelector);
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
     * @return prepared current tour step or undefined
     */
    private getPreparedTourStep(): TourStep | undefined {
        if (!this.currentTour) {
            return undefined;
        }
        return this.checkSelectorValidity()
            ? this.setTourOrientation(this.currentTour.steps[this.currentTourStepIndex])
            : GuidedTourService.setStepAlreadyFinishedHint(this.currentTour.steps[this.currentTourStepIndex]);
    }

    /**
     * Set the next prepared tour step
     */
    private setPreparedTourStep(): void {
        const preparedTourStep = this.getPreparedTourStep();
        if (preparedTourStep) {
            this.guidedTourCurrentStepSubject.next(preparedTourStep);
        } else {
            this.nextStep();
        }
    }

    /**
     * Set orientation of the passed on tour step
     * @param step passed on tour step of a guided tour
     * @return guided tour step with defined orientation
     */
    private setTourOrientation(step: TourStep): TourStep | undefined {
        if (!step) {
            return undefined;
        }
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

    /** If the current tour step cannot be displayed because it has already been successfully completed, then this
     * extra TourStep should be displayed instead
     */
    private static setStepAlreadyFinishedHint(step: any): TourStep | undefined {
        if (step.skipStepIfNoSelector) {
            return undefined;
        }
        return new TextTourStep({
            headlineTranslateKey: step.headlineTranslateKey,
            contentTranslateKey: step.contentTranslateKey,
            alreadyExecutedTranslateKey: 'tour.stepAlreadyExecutedHint.text',
        });
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
            throw new Error('Cannot update non-existing guided tour settings');
        }
        const existingSettingIndex = this.guidedTourSettings.findIndex((setting) => setting.guidedTourKey === guidedTourKey);
        if (existingSettingIndex !== -1) {
            this.guidedTourSettings[existingSettingIndex].guidedTourStep = guidedTourStep;
            this.guidedTourSettings[existingSettingIndex].guidedTourState = guidedTourState;
        } else {
            this.guidedTourSettings.push(new GuidedTourSetting(guidedTourKey, guidedTourStep, guidedTourState));
        }
        return this.http.put<GuidedTourSetting[]>(this.resourceUrl, this.guidedTourSettings, { observe: 'response' });
    }

    /**
     * Send a DELETE request to delete the guided tour settings of the current user
     * @param guidedTourKey the guided_tour_key of the tour setting that should be deleted
     * @return Observable<EntityResponseType>: updated guided tour settings
     */
    private deleteGuidedTourSetting(guidedTourKey: string): Observable<EntityResponseType> {
        if (!this.guidedTourSettings) {
            this.resetTour();
            throw new Error('Cannot update non-existing guided tour settings');
        }

        const index = this.guidedTourSettings.findIndex((setting) => setting.guidedTourKey === guidedTourKey);
        this.guidedTourSettings.splice(index, 1);

        return this.http.delete<GuidedTourSetting[]>(`${this.resourceUrl}/${guidedTourKey}`, { observe: 'response' });
    }

    /**
     * Enable a given tour for the component that calls this method and make the start tour button in the navigation bar availability
     * by setting the guidedTourAvailability to true
     *
     * @param guidedTour that should be enabled for the current component
     * @param init - if true - enables the display of the guided tour on the first visit, this parameter is used for guided tours which navigate through multiple component pages
     */
    private enableTour(guidedTour: GuidedTour, init: boolean) {
        /**
         * Set timeout so that the reset of the previous guided tour on the navigation end can be processed first
         * to prevent ExpressionChangedAfterItHasBeenCheckedError
         */
        setTimeout(() => {
            this.availableTourForComponent = cloneDeep(guidedTour);
            this.guidedTourAvailabilitySubject.next(true);
            const hasStartedOrFinishedTour = this.checkTourState(guidedTour);
            // Only start tour automatically if the user has never seen it before
            if (!hasStartedOrFinishedTour && init) {
                this.startTour();
            }
        }, 500);
    }

    /**
     * Check if the course and exercise for the tour are available on the course-exercise component
     * @param course for which the guided tour availability should be checked
     * @param guidedTour that should be enabled
     * @param init - if true - enables the display of the guided tour on the first visit, this parameter is used for guided tours which navigate through multiple component pages
     */
    public enableTourForCourseExerciseComponent(course: Course | undefined, guidedTour: GuidedTour, init: boolean): Exercise | undefined {
        if (!course || !course.exercises || !this.isGuidedTourAvailableForCourse(course)) {
            return undefined;
        }

        const exerciseForGuidedTour = course.exercises.find((exercise) => this.isGuidedTourAvailableForExercise(exercise, guidedTour));
        if (exerciseForGuidedTour) {
            this.enableTour(guidedTour, init);
            this.currentCourse = course;
            this.currentExercise = exerciseForGuidedTour;
            return exerciseForGuidedTour;
        }
        return undefined;
    }

    /**
     * Check if the course list contains the course for which the tour is available
     * @param courses which can contain the needed course for the tour
     * @param guidedTour that should be enabled
     * @param init - if true - enables the display of the guided tour on the first visit, this parameter is used for guided tours which navigate through multiple component pages
     */
    public enableTourForCourseOverview(courses: Course[], guidedTour: GuidedTour, init: boolean): Course | undefined {
        const courseForTour = courses.find((course) => this.isGuidedTourAvailableForCourse(course));
        if (!courseForTour) {
            return undefined;
        }

        if (this.guidedTourMapping!.tours[guidedTour.settingsKey] === '') {
            this.currentCourse = courseForTour;
            this.enableTour(guidedTour, init);
        } else {
            this.courseService.findWithExercises(courseForTour.id!).subscribe((response) => {
                if (response.body?.exercises) {
                    const exercises = response.body.exercises;
                    const exerciseForTour = exercises.find((exercise) => this.isGuidedTourAvailableForExercise(exercise, guidedTour));

                    this.currentCourse = courseForTour;
                    this.currentExercise = exerciseForTour ? exerciseForTour : undefined;
                }
                this.enableTour(guidedTour, init);
            });
        }

        return courseForTour;
    }

    /**
     * Check if the exercise list contains the exercise for which the tour is available
     * @param exercise which can contain the needed exercise for the tour
     * @param guidedTour that should be enabled
     * @param init - if true - enables the display of the guided tour on the first visit, this parameter is used for guided tours which navigate through multiple component pages
     */
    public enableTourForExercise(exercise: Exercise, guidedTour: GuidedTour, init: boolean): Exercise | undefined {
        if (!exercise.course || !this.isGuidedTourAvailableForExercise(exercise, guidedTour)) {
            return undefined;
        }

        this.enableTour(guidedTour, init);
        this.currentExercise = exercise;
        this.currentCourse = exercise.course;

        return exercise;
    }

    /**
     * Determine if the current course is a course for a guided tour
     * @param course    current course
     * @return true if the current course is a course for a guided tour, otherwise false
     */
    private isGuidedTourAvailableForCourse(course: Course): boolean {
        if (!course || !this.guidedTourMapping) {
            return false;
        }
        return course.shortName === this.guidedTourMapping.courseShortName;
    }

    /**
     * Determine if the current exercise is an exercise for a guided tour
     * @param exercise  current exercise
     * @param guidedTour of which the availability should be checked
     * @return true if the current exercise is an exercise for a guided tour, otherwise false
     */
    private isGuidedTourAvailableForExercise(exercise: Exercise, guidedTour?: GuidedTour): boolean {
        if (!exercise || !this.guidedTourMapping) {
            return false;
        }

        let exerciseMatches: boolean;
        let settingsKey: string;

        if (guidedTour) {
            settingsKey = guidedTour.settingsKey;
        } else {
            settingsKey = this.currentTour ? this.currentTour.settingsKey : '';
        }

        if (exercise.type === ExerciseType.PROGRAMMING) {
            exerciseMatches = this.guidedTourMapping.tours[settingsKey] === exercise.shortName;
        } else {
            exerciseMatches = this.guidedTourMapping.tours[settingsKey] === exercise.title;
        }
        return exerciseMatches;
    }

    /**
     * Checks the assessment result and enables the next step click if correct
     * @param assessments   current number of assessments
     * @param totalScore    current total score of the assessment
     */
    public updateAssessmentResult(assessments: number, totalScore: number) {
        this.assessmentObject.assessments = assessments;
        this.assessmentObject.totalScore = totalScore;

        if (this.isAssessmentCorrect()) {
            this.enableNextStepClick();
        }
    }

    /**
     * Returns true if the number of assessments and its total score match with the given assessment task object
     */
    private isAssessmentCorrect(): boolean {
        if (this.currentStep.assessmentTask) {
            const numberOfAssessmentsCorrect = this.assessmentObject.assessments === this.currentStep.assessmentTask.assessmentObject.assessments;
            const totalScoreCorrect = this.assessmentObject.totalScore === this.currentStep.assessmentTask.assessmentObject.totalScore;

            if (this.currentStep.assessmentTask.assessmentObject.totalScore === 0) {
                return numberOfAssessmentsCorrect;
            }
            return numberOfAssessmentsCorrect && totalScoreCorrect;
        } else {
            return false;
        }
    }
}
