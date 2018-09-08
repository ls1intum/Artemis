import { downgradeComponent, downgradeInjectable } from '@angular/upgrade/static';
import { FactoryProvider } from '@angular/core';
import * as angular from 'angular';

export interface IComponentUpgradeOptions {
    /**
     * @param inputs: An array of strings that specify what inputs the component accepts
     * @param outputs: An array of strings that specify what outputs the component accepts
     */
    inputs?: string[];
    outputs?: string[];
}

/**
 * @interface IHybridHelper
 * @desc Provides several functions to simplify the up- and downgrade process for components and services.
 * The helper is a wrapper for the Angular upgrade and downgrade API.
 * It helps us to separate our application from the changes of angular hybrid process.
 */
export interface IHybridHelper {
    downgradeComponent(moduleName: string, componentSelector: string, componentClass: any, options?: IComponentUpgradeOptions): IHybridHelper;
    downgradeProvider(moduleName: string, providerName: string, providerClass: any): IHybridHelper;
    buildProviderForUpgrade(ng1Name: string, ng2Name?: string): FactoryProvider;
    buildFactoryForUpgradeProvider(ng1Name: string): Function;
}

export const HybridHelper: IHybridHelper = {
    /**
     *
     * @param {string} moduleName: name of the module which contains the component to be downgraded
     * @param {string} componentName: name of the component to be downgraded
     * @param componentClass: component class to be downgraded
     * @param options: optional, used to specify inputs and outputs for the component
     * @returns {IHybridHelper}
     * @desc Downgrade a ng5 component which then can be used in the ng1 app
     */
    downgradeComponent: (moduleName: string, componentName: string, componentClass: any, options?: IComponentUpgradeOptions): IHybridHelper => {
        options = options || {};
        const inputs = options.inputs || [];
        const outputs = options.outputs || [];
        const component = componentClass;

        angular.module(moduleName).directive(componentName, downgradeComponent({
            component, inputs, outputs
        }) as angular.IDirectiveFactory);

        return HybridHelper;
    },

    /**
     * @function downgradeProvider
     * @param {string} moduleName: name of the module which contains the service to be downgraded
     * @param {string} providerName: name of the service to be downgraded
     * @param providerClass
     * @returns {IHybridHelper}
     * @desc Downgrade a ng5 service which then can be used in the ng1 app
     */
    downgradeProvider: (moduleName: string, providerName: string, providerClass: any): IHybridHelper => {
        angular.module(moduleName).factory(providerName, downgradeInjectable(providerClass));

        return HybridHelper;
    },

    /**
     * @function buildProviderForUpgrade
     * @param {string} ng1Name: exact name of the service to be upgraded
     * @param {string} ng2Name: desired name for the provided service in the ng5 app (optional)
     * @returns {FactoryProvider}
     * @desc Return the declaration which can be used in the app's providers array to upgrade a service.
     */
    buildProviderForUpgrade: (ng1Name: string, ng2Name?: string): FactoryProvider => {
        ng2Name = ng2Name || ng1Name;

        return {
            provide: ng2Name,
            useFactory: this.buildFactoryForUpgradeProvider(ng1Name),
            deps: ['$injector']
        };
    },

    /**
     * @function buildFactoryForUpgradeProvider
     * @param {string} ng1Name: exact name of the service to be upgraded
     * @returns {Function}
     * @desc Provides the injection token for the ng1 service which has to be upgraded
     */
    buildFactoryForUpgradeProvider(ng1Name: string): Function {
        return (injector: any) => injector.get(ng1Name);
    }
};
