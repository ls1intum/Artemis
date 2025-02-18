import { Subject } from 'rxjs';
import { TemplateRef } from '@angular/core';
import { EventEmitter } from '@angular/core';

export interface BarControlConfiguration {
    subject?: Subject<TemplateRef<any>>;
}

export interface BarControlConfigurationProvider {
    controlConfiguration: BarControlConfiguration;
    controlsRendered: EventEmitter<void>;
}
