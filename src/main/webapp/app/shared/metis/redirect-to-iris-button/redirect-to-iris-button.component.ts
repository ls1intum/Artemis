import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/communication/entities/conversation/channel.model';
import { IrisCourseSettings, IrisExerciseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { Subscription, catchError, of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Router } from '@angular/router';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { MetisService } from 'app/communication/metis.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';

@Component({
    selector: 'jhi-redirect-to-iris-button',
    templateUrl: './redirect-to-iris-button.component.html',
    imports: [IrisLogoComponent, NgClass, FaIconComponent],
})
export class RedirectToIrisButtonComponent implements OnInit, OnDestroy {
    buttonLoading = input<boolean>(false);
    disabled = input<boolean>(false);
    question = input<string>();
    course = input<Course>();
    extraClass = input<any>();

    metisConversationService = inject(MetisConversationService);
    protected metisService = inject(MetisService);
    irisSettingsService = inject(IrisSettingsService);
    router = inject(Router);

    private conversationServiceSubscription: Subscription;
    private irisCombinedExerciseSettings: IrisExerciseSettings | undefined;
    private irisCombinedCourseSettings: IrisCourseSettings | undefined;
    channelSubTypeReferenceRouterLink = '';
    irisEnabled = signal<boolean>(false);

    // Icons and sizes
    faCircleNotch = faCircleNotch;
    TEXT = IrisLogoSize.TEXT;

    ngOnInit(): void {
        this.conversationServiceSubscription = this.metisConversationService.activeConversation$.subscribe((conversation) => {
            this.checkIrisSettings(getAsChannelDTO(conversation));
        });
    }

    ngOnDestroy(): void {
        this.conversationServiceSubscription?.unsubscribe();
    }

    /**
     * Helper method to reduce duplicate logic for checking and updating Iris status.
     *
     * @param cachedSettings Previously fetched settings, if available.
     * @param fetchSettings Function to fetch settings if not cached.
     * @param extractEnabled Function to extract the enabled flag from the settings.
     * @param channelDTO The current channel DTO.
     * @param cacheSetter Function to store fetched settings.
     */
    private updateIrisStatus<T>(
        cachedSettings: T | undefined,
        fetchSettings: () => any,
        extractEnabled: (settings: T) => boolean | undefined,
        channelDTO: ChannelDTO,
        cacheSetter: (settings: T) => void,
    ): void {
        if (cachedSettings) {
            this.setIrisStatus(extractEnabled(cachedSettings), channelDTO);
        } else {
            fetchSettings()
                .pipe(
                    catchError(() => {
                        this.setIrisStatus(false, channelDTO);
                        return of(undefined);
                    }),
                )
                .subscribe((newSettings: T) => {
                    if (newSettings) {
                        cacheSetter(newSettings);
                        this.setIrisStatus(extractEnabled(newSettings), channelDTO);
                    }
                });
        }
    }

    /**
     * Checks if Iris is activated in the Iris Settings
     * @param channelDTO channel DTO that contains the needed information to be checked
     * @private
     */
    private checkIrisSettings(channelDTO?: ChannelDTO): void {
        if (!channelDTO) {
            return this.setIrisStatus();
        }
        switch (channelDTO.subType) {
            case ChannelSubType.GENERAL: {
                const course = this.course();
                if (course?.studentCourseAnalyticsDashboardEnabled && course.id) {
                    this.updateIrisStatus<IrisCourseSettings>(
                        this.irisCombinedCourseSettings,
                        () => this.irisSettingsService.getCombinedCourseSettings(course.id!),
                        (settings) => settings.irisCourseChatSettings?.enabled,
                        channelDTO,
                        (settings) => (this.irisCombinedCourseSettings = settings),
                    );
                } else {
                    this.setIrisStatus();
                }
                break;
            }
            case ChannelSubType.LECTURE: {
                if (channelDTO.subTypeReferenceId) {
                    this.updateIrisStatus<IrisCourseSettings>(
                        this.irisCombinedCourseSettings,
                        () => this.irisSettingsService.getCombinedCourseSettings(channelDTO.subTypeReferenceId!),
                        (settings) => settings.irisLectureChatSettings?.enabled,
                        channelDTO,
                        (settings) => (this.irisCombinedCourseSettings = settings),
                    );
                } else {
                    this.setIrisStatus();
                }
                break;
            }
            case ChannelSubType.EXERCISE: {
                if (channelDTO.subTypeReferenceId) {
                    this.updateIrisStatus<IrisExerciseSettings>(
                        this.irisCombinedExerciseSettings,
                        () => this.irisSettingsService.getCombinedExerciseSettings(channelDTO.subTypeReferenceId!),
                        (settings) => settings.irisChatSettings?.enabled,
                        channelDTO,
                        (settings) => (this.irisCombinedExerciseSettings = settings),
                    );
                } else {
                    this.setIrisStatus();
                }
                break;
            }
            default:
                this.setIrisStatus();
                break;
        }
    }

    /**
     * Updates the Iris enabled status and sets the router link if enabled.
     *
     * @param enabled Whether Iris is enabled.
     * @param channelDTO The channel data transfer object.
     */
    private setIrisStatus(enabled: boolean = false, channelDTO?: ChannelDTO): void {
        this.irisEnabled.set(enabled);
        this.channelSubTypeReferenceRouterLink = enabled ? (this.metisService.getLinkForChannelSubType(channelDTO) ?? '') : '';
    }

    /**
     * Redirects to Iris with the current question as a query parameter.
     */
    redirectToIris(): void {
        const content = this.question();
        this.router.navigate([this.channelSubTypeReferenceRouterLink], { queryParams: { irisQuestion: content } });
    }
}
