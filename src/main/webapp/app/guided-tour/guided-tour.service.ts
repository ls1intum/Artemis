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

@Injectable({ providedIn: 'root' })
export class GuidedTourService {
    public resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';

    public guidedTourCurrentStepStream: Observable<TourStep | null>;
    public currentTourSteps: TourStep[];
    public guidedTourSettings: GuidedTourSettings;

    private guidedTourCurrentStepSubject = new Subject<TourStep | null>();
    private currentTourStepIndex = 0;
    private currentTour: GuidedTour | null;
    private onFirstStep = true;
    private onLastStep = true;
    private onResizeMessage = false;

    private guidedTourNotification = new Subject<any>();

    constructor(public errorHandler: ErrorHandler, private http: HttpClient, private jhiAlertService: JhiAlertService, private accountService: AccountService) {
        this.getGuidedTourSettings();

        this.guidedTourCurrentStepStream = this.guidedTourCurrentStepSubject.asObservable();

        fromEvent(window, 'resize')
            .pipe(debounceTime(200))
            .subscribe(() => {
                if (this.currentTour && this.currentTourStepIndex > -1) {
                    if (this.currentTour.minimumScreenSize && this.currentTour.minimumScreenSize >= window.innerWidth) {
                        this.onResizeMessage = true;
                        this.guidedTourCurrentStepSubject.next({
                            headlineTranslateKey: 'tour.resize.headline',
                            contentType: ContentType.TEXT,
                            contentTranslateKey: 'tour.resize.content',
                        });
                    } else {
                        this.onResizeMessage = false;
                        this.guidedTourCurrentStepSubject.next(this.getPreparedTourStep(this.currentTourStepIndex));
                    }
                }
            });
    }

    /**
     * Notify other component to start guided tour from service
     * @param component     name of the component as string
     */
    startTourForComponent(component: string) {
        this.guidedTourNotification.next({ name: component });
    }

    /**
     * Get guided tour notifications from service
     */
    getGuidedTourNotification(): Observable<any> {
        return this.guidedTourNotification.asObservable();
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
        if (!this.currentTour) {
            return;
        }
        const currentStep = this.currentTour.steps[this.currentTourStepIndex];
        if (currentStep.closeAction) {
            currentStep.closeAction();
        }
        if (this.currentTour.steps[this.currentTourStepIndex + 1]) {
            this.currentTourStepIndex++;
            this._setFirstAndLast();
            if (currentStep.action) {
                currentStep.action();
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
            if (this.currentTour.completeCallback) {
                this.currentTour.completeCallback();
            }
            this.updateGuidedTourSettings(this.currentTour.settingsId, false).subscribe(guidedTourSettings => {
                if (guidedTourSettings.body) {
                    this.guidedTourSettings = guidedTourSettings.body;
                }
            });
            this.resetTour();
        }
    }

    /**
     * Navigate to previous tour step
     */
    public backStep(): void {
        if (this.currentTour) {
            const currentStep = this.currentTour.steps[this.currentTourStepIndex];
            if (currentStep.closeAction) {
                currentStep.closeAction();
            }
            if (this.currentTour.steps[this.currentTourStepIndex - 1]) {
                this.currentTourStepIndex--;
                this._setFirstAndLast();
                if (currentStep.action) {
                    currentStep.action();
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
    }

    /**
     * Skip tour
     */
    public skipTour(): void {
        if (this.currentTour) {
            if (this.currentTour.skipCallback) {
                this.currentTour.skipCallback(this.currentTourStepIndex);
            }
            this.resetTour();
        }
    }

    /**
     * Close tour and remove overlay
     */
    public resetTour(): void {
        document.body.classList.remove('tour-open');
        this.currentTour = null;
        this.currentTourStepIndex = 0;
        this.guidedTourCurrentStepSubject.next(null);
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

        this.currentTour = cloneDeep(tour);
        this.currentTour.steps = this.currentTour.steps.filter(step => !step.skipStep);
        this.currentTourStepIndex = 0;
        this._setFirstAndLast();
        if (this.currentTour.steps.length > 0 && (!this.currentTour.minimumScreenSize || window.innerWidth >= this.currentTour.minimumScreenSize)) {
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
     * Define first and last tour step based on amount of tour steps
     * @private
     */
    private _setFirstAndLast(): void {
        if (this.currentTour) {
            this.onLastStep = this.currentTour.steps.length - 1 === this.currentTourStepIndex;
            this.onFirstStep = this.currentTourStepIndex === 0;
        }
    }

    /* Check if highlighted element is available */
    private checkSelectorValidity(): boolean {
        if (!this.currentTour) {
            return false;
        }
        if (this.currentTour.steps[this.currentTourStepIndex].selector) {
            const selectedElement = document.querySelector(this.currentTour.steps[this.currentTourStepIndex].selector!);
            if (!selectedElement) {
                this.errorHandler.handleError(
                    // If error handler is configured this should not block the browser.
                    new Error(
                        `Error finding selector ${this.currentTour.steps[this.currentTourStepIndex].selector} on step ${this.currentTourStepIndex + 1} during guided tour: ${
                            this.currentTour.settingsId
                        }`,
                    ),
                );
                return false;
            }
        }
        return true;
    }

    /**
     *  Is last tour step
     */
    public get isOnLastStep(): boolean {
        return this.onLastStep;
    }

    /**
     * Is first tour step
     */
    public get isOnFirstStep(): boolean {
        return this.onFirstStep;
    }

    /* Show resize message */
    public get isOnResizeMessage(): boolean {
        return this.onResizeMessage;
    }

    /* Current tour step number */
    public get currentTourStepDisplay(): number {
        return this.currentTourStepIndex + 1;
    }

    /* Total count of tour steps */
    public get currentTourStepCount(): any {
        return this.currentTour && this.currentTour.steps ? this.currentTour.steps.length : 0;
    }

    /* Prevents the tour from advancing by clicking the backdrop */
    public get preventBackdropFromAdvancing(): boolean {
        if (this.currentTour) {
            return this.currentTour && (this.currentTour.preventBackdropFromAdvancing ? this.currentTour.preventBackdropFromAdvancing : false);
        }
        return false;
    }

    /**
     * Get the tour step with defined orientation
     * @param index current tour step index
     */
    private getPreparedTourStep(index: number): TourStep | undefined {
        if (this.currentTour) {
            return this.setTourOrientation(this.currentTour.steps[index]);
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
