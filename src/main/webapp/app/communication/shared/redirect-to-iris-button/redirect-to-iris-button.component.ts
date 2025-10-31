import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { CourseIrisSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { Subscription, catchError, distinctUntilKeyChanged, filter, of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { MetisService } from 'app/communication/service/metis.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';

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
    profileService = inject(ProfileService);
    router = inject(Router);

    private conversationServiceSubscription: Subscription;
    private settingsSubscription: Subscription | undefined;
    private irisCourseSettings: CourseIrisSettingsDTO | undefined;
    channelSubTypeReferenceRouterLink = '';
    irisEnabled = signal<boolean>(false);

    // Icons and sizes
    faCircleNotch = faCircleNotch;
    TEXT = IrisLogoSize.TEXT;

    ngOnInit(): void {
        const isIrisActive = this.profileService.isProfileActive(PROFILE_IRIS);
        this.irisEnabled.set(isIrisActive);
        if (!isIrisActive) {
            return;
        }
        this.conversationServiceSubscription = this.metisConversationService.activeConversation$
            .pipe(
                filter((conversation) => !!conversation),
                distinctUntilKeyChanged('id'),
            )
            .subscribe((conversation) => {
                this.checkIrisSettings(getAsChannelDTO(conversation));
            });
    }

    ngOnDestroy(): void {
        this.conversationServiceSubscription?.unsubscribe();
        this.settingsSubscription?.unsubscribe();
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
            this.settingsSubscription = fetchSettings()
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
            case ChannelSubType.GENERAL:
            case ChannelSubType.LECTURE:
            case ChannelSubType.EXERCISE: {
                const course = this.course();
                if (course?.id) {
                    this.updateIrisStatus<CourseIrisSettingsDTO>(
                        this.irisCourseSettings,
                        () => this.irisSettingsService.getCourseSettings(course.id!),
                        (settings) => settings.settings?.enabled,
                        channelDTO,
                        (settings) => (this.irisCourseSettings = settings),
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
