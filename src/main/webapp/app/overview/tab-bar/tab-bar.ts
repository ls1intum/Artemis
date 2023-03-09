import { TemplateRef } from '@angular/core';
import { Subject } from 'rxjs';

export interface BarControlConfiguration {
    subject?: Subject<TemplateRef<any>>;
}

export interface BarControlConfigurationProvider {
    controlConfiguration: BarControlConfiguration;
}
