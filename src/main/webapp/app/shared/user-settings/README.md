# Developer Guide for using and extending user settings

## General Structure
* The data/interface/class **hierarchy** is explained in `user-settings.service.ts` (look at the **interfaces**).

## How to create new "child"- settings
*Look at the `notification-settings` directory as a prime **example** for child-settings*

**Steps :**
1)  **Add a new route** for the new settings in `user-settings.route.ts`. *(under `children:`)*
    
    Add your option to the `user-settings-container.component.html` *(under `<!-- links to different settings -->`)*

2) **Create a new folder** for `childSettings` and **put** `childSettings` specific files into it

3) a) Use the `notification-settings.component.html`(**copy**) and `user-settings.scss`(**link**) in `child-settings` to **reuse** the same UI (just replace the relevant OptionCore part)<br>

   b) Create a new `child-settings.default.ts` file and create your options **based on the user-settings hierarchy**<br>
   * Add a new `X-OptionCore` that **extends** `OptionCore` and define the needed properties for `child-settings`
   * Add the new `category` to `user-settings.constants.ts` *(under webapp/shared/constants)*
               (look at `notification-settings.default.ts` for an example)<br>
     ```ts
     // General Structure for child-settings.default.ts
     
     // define new concrete implementation for OptionCore
     export interface ChildOptionCore extends OptionCore {
        // child specific/unique properties that should be saved in the DB
        propertyA: boolean;
        //... more properties
     }
     // write/create the default settings object for child-settings
     export const defaultChildrenSettings: UserSettings<ChildOptionCore> = {
        category: UserSettingsCategory.CHILD_SETTINGS,
        groups: [
           {
              name: 'GroupNameA',
              restrictionLevel: Authority.USER,
              options: [{
                 name: 'OptionA',
                 description: 'Description for OptionA',
                 optionCore: {
                    propertyA: true
                    //... more properties with concretely set default values
                 }
              //... more Options
              }]
           }
           //... more OptionGroups
     ]}
     ```
   c) Be **careful and precise** with the **naming** of new `optionSpecifiers`. Use them to create a mapping/correspondence to the actual changes in system-logic/behavior
   * These names correspond with other places where **mapping** and **translation** take place
        **Translation :**<br>
     TranslationFile : `userSettings.json`
   * Example : `{{ 'artemisApp.userSettings.optionGroupNames.' + optionGroup.name | artemisTranslate }}`
     with `optionGroup.name` = *'Exercise Notifications'* <br>
     The structure of `userSettings.json` :
     ```json
      {
       "artemisApp": {
       "userSettings": {
        "...": "...",
       "optionGroupNames": {
       "Exercise Notifications": "Exercise Notifications",
        "...": "..."
       }}}
      }
      ```
4) Create a new `child-settings.component.ts` file :
   * **Extend** from `user-settings.directive` *(if you want to reuse its functionality)* and implement `OnInit` 
   * Place the relevant Services for the parent(prototype) component in the constructor
   * Inside `ngOnInit()` call `super.ngOnInit()`, afterwards set the child specific `userSettingsCategory`*(same as in default.ts)* and `changeEventMessage`
     ```ts
        @Component
        export class UserSettingsComponent extends UserSettingsDirective implements OnInit {
        constructor(userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
        super(userSettingsService, alertService, changeDetector);
        }
            userSettings: UserSettings<ChildOptionCore>;
            optionCores: Array<ChildOptionCore>;
        
            ngOnInit(): void {
                this.userSettingsCategory = UserSettingsCategory.CHILD_SETTINGS;
                this.changeEventMessage = childSettingsChangeMessage;
                super.ngOnInit();
            }
        }
     ```
5) For further child specific logic e.g. add a new `child-settings.service` file, new custom template/scss, etc.

## Server Side :
For every new child-settings you have to create a new **table**, **REST controller/resource** (service) and repository due to the possibly big differences between the `OptionCores`.
            *(Might change due to found similarities)*

## Additional Information
For further reading see the original PR that introduces these settings : https://github.com/ls1intum/Artemis/pull/3922
