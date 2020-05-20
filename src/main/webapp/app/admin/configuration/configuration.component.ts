import { Component, OnInit } from '@angular/core';
import { JhiConfigurationService } from 'app/admin/configuration/configuration.service';

@Component({
    selector: 'jhi-configuration',
    templateUrl: './configuration.component.html',
})
export class JhiConfigurationComponent implements OnInit {
    allConfiguration: any = null;
    configuration: any = null;
    configKeys: any[];
    filter: string;
    orderProp: string;
    reverse: boolean;

    constructor(private configurationService: JhiConfigurationService) {
        this.configKeys = [];
        this.filter = '';
        this.orderProp = 'prefix';
        this.reverse = false;
    }

    // tslint:disable-next-line:completed-docs
    keys(dict: any): Array<string> {
        return dict === undefined ? [] : Object.keys(dict);
    }

    /** sets the configuration on init */
    ngOnInit() {
        this.configurationService.get().subscribe((configuration: any) => {
            this.configuration = configuration;

            for (const config of configuration) {
                if (config.properties !== undefined) {
                    this.configKeys.push(Object.keys(config.properties));
                }
            }
        });

        this.configurationService.getEnv().subscribe((configuration: any) => {
            this.allConfiguration = configuration;
        });
    }
}
