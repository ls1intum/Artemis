import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { Option, OptionCore, OptionGroup, SettingsCategory } from 'app/shared/user-settings/user-settings.component';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['../user-settings.component.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    notificationSettings: SettingsCategory;
    optionsChanged: boolean = false;
    page = 0;
    error?: string;
    notificationOptionCores: Array<OptionCore>;

    constructor(private notificationService: NotificationService) {}

    ngOnInit(): void {
        this.notificationSettings = defaultNotificationSettings;
        this.notificationOptionCores = new Array<OptionCore>();
        this.loadNotificationOptions();
    }

    saveOptions() {
        //TODO refresh notifications in notification-sidebar (else outdated, ngOnitnit only called once, i.e. only calls loadnotifications once)

        let newOptionCores = this.notificationOptionCores.filter((optionCore) => optionCore.id === -1);
        this.notificationService.saveNewUserOptions(newOptionCores).subscribe(
            (res: HttpResponse<OptionCore[]>) => this.saveUserOptionsSuccess(res.body!, res.headers),
            (res: HttpErrorResponse) => (this.error = res.message),
        );

        //only save those that got changed
        let changedUserOptions = this.notificationOptionCores.filter((option) => option.id != -1 && option.changed);
        /*
        this.notificationService
            .saveChangedUserOptions(this.notificationOptions)
            .subscribe(
                (res: HttpResponse<UserOption[]>) => this.saveUserOptionsSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
         */
    }

    private saveUserOptionsSuccess(receivedOptionCores: OptionCore[], headers: HttpHeaders): void {
        debugger;
        /*
        if(this.notificationOptionCores.length === receivedOptionCores.length) {
            this.notificationOptionCores = receivedOptionCores;
        }
         */
        // create Option Groups and SettingsCategory from received User Options
        this.updateSettings(receivedOptionCores);
    }

    toggleOption(event: any) {
        this.optionsChanged = true;
        const optionId = event.currentTarget.id; //TODO
        //let foundOption = this.notificationOptions.find((option) => option.type === notificationType);
        let foundOption = this.notificationOptionCores.find((core) => core.optionId === optionId);
        if (!foundOption) return;
        foundOption!.webapp = !foundOption!.webapp;
        foundOption.changed = true;
    }

    private loadNotificationOptions(): void {
        this.notificationService
            .queryUserOptions({
                page: this.page, //kp ob nötig
            })
            .subscribe(
                (res: HttpResponse<OptionCore[]>) => this.loadNotificationOptionCoresSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
    }
    /*
    private saturateDefaultUserOptionsWithGroupAndCategoryInformation(options: OptionCore[], categoryName: string, groupName: string): UserOption[] {
        options.forEach((option) => {
            option.category = categoryName;
            option.group = groupName;
            option.id = -1; // to indicate that this option is not yet represented in the database
            //kp vll user & id noch?
        });
        return options;
    }
 */
    private extractOptionCoresFromSettingsCategory(settings: SettingsCategory): OptionCore[] {
        //   const categoryName = settings.name;
        let optionCoreAccumulator: OptionCore[] = [];
        settings.groups.forEach((group: OptionGroup) => {
            //const saturatedUserOptions = this.saturateDefaultUserOptionsWithGroupAndCategoryInformation(group.options, categoryName, groupName);
            //optionCoreAccumulator = optionCoreAccumulator.concat(saturatedUserOptions);
            group.options.forEach((option: Option) => {
                optionCoreAccumulator.push(option.optionCore);
            });
            //let tmpOptionCores : OptionCore[] = group.options.map((option : Option) => { option.optionCore; })
        });
        return optionCoreAccumulator;
    }

    private updateSettings(newOptionCores: OptionCore[]): void {
        //update the user option cores

        //Gehe durch alle defaults (lokal/client bereits geladenen) cores durch und falls es ein passendes neues gibt updaten.
        /* vll unnötig, da ich einfach zuerst die settings austauschen kann und dann nochmal extracten
        for(let i = 0; i < this.notificationOptionCores.length; i++) {
            const oldCoreId = this.notificationOptionCores[i].optionId;
            const matchingNewCore = newOptionCores.find(newCore => newCore.optionId === oldCoreId);
            if(matchingNewCore != undefined) {
                this.notificationOptionCores[i] = matchingNewCore;
            }
        }
         */

        //use the updated cores to update the entire settings category

        for (let i = 0; i < this.notificationSettings.groups.length; i++) {
            for (let j = 0; j < this.notificationSettings.groups[i].options.length; j++) {
                //this.notificationSettings.groups[i].options.find(option => option.optionCore.optionId === )
                //this.notificationSettings.groups[i].options
                const currentOptionCore = this.notificationSettings.groups[i].options[j].optionCore;
                const matchingOptionCore = newOptionCores.find((newCore) => newCore.optionId === currentOptionCore.optionId);
                if (matchingOptionCore != undefined) {
                    this.notificationSettings.groups[i].options[j].optionCore = matchingOptionCore;
                }
            }
        }

        this.notificationOptionCores = this.extractOptionCoresFromSettingsCategory(this.notificationSettings);
        /*
        const groups = this.getGroups(this.notificationOptions)
        this.notificationSettings.groups = groups as OptionGroup[];
 */
    }

    private loadNotificationOptionCoresSuccess(receivedNotificationOptionCores: OptionCore[], headers: HttpHeaders): void {
        debugger;

        // if no option cores were loaded -> user has not yet changed options -> use default notification settings
        if (receivedNotificationOptionCores == undefined || receivedNotificationOptionCores.length === 0) {
            this.notificationSettings = defaultNotificationSettings;
            this.notificationOptionCores = this.extractOptionCoresFromSettingsCategory(defaultNotificationSettings);
            return;
        }

        // else user already customized the settings
        // normal use case where no new options had been patched but only the old ones are fetched
        /*
        if(this.notificationOptionCores.length === receivedNotificationOptionCores.length) {
            this.notificationOptionCores = receivedNotificationOptionCores;
        }
         */
        // create Option Groups and SettingsCategory from fetched user option cores
        this.updateSettings(receivedNotificationOptionCores);
    }

    // Default notification settings

    //TODO
}
