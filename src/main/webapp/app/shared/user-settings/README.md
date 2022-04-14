# Developer Guide for using and extending user settings

## General Structure

-   The data/interface/class **hierarchy** is explained in `user-settings.service.ts` (look at the **interfaces**).

## How to create new "child"- settings

_Look at the `notification-settings` directory as a prime **example** for child-settings_

**Steps :**

1.  **Add a new route** for the new settings in `user-settings.route.ts` _(under `children:`)_

    Add the new settings page to the `user-settings-container.component.html` _(under `<!-- links to different settings -->`)_

2.  **Create a new folder** for `childSettings` and **put** `childSettings` specific files into it

3.  a) Use the `notification-settings.component.html`(**copy**) and `user-settings.scss`(**link**) in `child-settings` to **reuse** the same UI (just replace the relevant Setting part)<br>

    b) Create a new `child-settings-structure.ts` file and create your individual settings **based on the user-settings hierarchy**<br>

    -   Add a new `X-Setting` interface that **extends** `Setting` and define the needed properties for `child-setting`
    -   Add the new `category` to `user-settings.constants.ts` _(under webapp/shared/constants)_
        (look at `notification-settings-structure.ts` for an example)<br>
        ```ts
        // General Structure for child-settings-structure.ts

        // define new concrete implementation for Setting
        export interface ChildSetting extends Setting {
            // child specific/unique properties that should be saved in the DB
            propertyA: boolean;
            //... more properties
        }
        // write/create the settings structure object for child-settings
        export const childrenSettingsStructure: UserSettings<ChildSetting> = {
            category: UserSettingsCategory.CHILD_SETTINGS,
            groups: [
                {
                    name: 'GroupNameA',
                    restrictionLevel: Authority.USER,
                    settings: [
                        {
                            name: 'SettingA',
                            description: 'Description for SettingA',
                            //... do not put the child specific properties here! The default values have to be stored on the server side!
                            //... more Settings
                        },
                    ],
                },
                //... more SettingGroups
            ],
        };
        ```
        c) Be **careful and precise** with the **naming** of new `SettingsIds`. Use them to create a mapping/correspondence to the actual changes in system-logic/behavior
    -   These names correspond with other places where **mapping** and **translation** take place
        **Translation :**<br>
        TranslationFile : `userSettings.json`
    -   Example : `{{ 'artemisApp.userSettings.settingGroupNames.' + settingGroup.key | artemisTranslate }}`
        with `settingGroup.key` = _'exerciseNotifications'_ <br>
        The structure of `userSettings.json` :
        ```json
        {
            "artemisApp": {
                "userSettings": {
                    "...": "...",
                    "settingGroupNames": {
                        "exerciseNotifications": "Exercise Notifications",
                        "...": "..."
                    },
                    "settingNames": {
                        "attachmentChanges": "Attachment Changes",
                        "...": "..."
                    },
                    "settingDescriptions": {
                        "attachmentChangesDescription": "Receive a notification when an attachment was changed",
                        "...": "..."
                    }
                }
            }
        }
        ```

4.  Create a new `child-settings.component.ts` file :
    -   **Extend** from `user-settings.directive` _(if you want to reuse its functionality)_ and implement `OnInit`
    -   Place the relevant Services for the parent(prototype) component in the constructor
    -   Inside `ngOnInit()` call `super.ngOnInit()`, afterwards set the child specific `userSettingsCategory`_(same as in default.ts)_ and `changeEventMessage`
        ```ts
        @Component
        export class UserSettingsComponent extends UserSettingsDirective implements OnInit {
            constructor(userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
                super(userSettingsService, alertService, changeDetector);
            }
            userSettings: UserSettings<Setting>;
            settings: Array<Setting>;

            ngOnInit(): void {
                this.userSettingsCategory = UserSettingsCategory.CHILD_SETTINGS;
                this.changeEventMessage = childSettingsChangeMessage;
                super.ngOnInit();
            }
        }
        ```
5.  For further child specific logic e.g. add a new `child-settings.service` file, new custom template/scss, etc.

## Server Side :

For every new child-settings you have to create a new **table**, **REST controller/resource** (service) ,
and repository due to the possibly big differences between the concrete `Setting` implementations.
_(Might change due to found similarities)_
Also do not forget to add the default settings. The idea is that the REST call to load settings should always return settings.
So either the user already changed the settings therefore they are present in the DB else the default settings should be returned.
Have a look at the NotificationSettingsService.java file for a good example for such default settings.

## Additional Information

For further reading see the original PR that introduces these settings : https://github.com/ls1intum/Artemis/pull/3922
