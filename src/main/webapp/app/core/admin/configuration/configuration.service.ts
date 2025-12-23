import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { Bean, Beans, ConfigProps, Env, PropertySource } from './configuration.model';

@Injectable({ providedIn: 'root' })
export class ConfigurationService {
    private http = inject(HttpClient);

    getBeans(): Observable<Bean[]> {
        return this.http.get<ConfigProps>('management/configprops').pipe(
            map((configProps) =>
                Object.values(
                    Object.values(configProps.contexts)
                        .map((context) => context.beans)
                        .reduce((allBeans: Beans, contextBeans: Beans) => ({ ...allBeans, ...contextBeans })),
                ),
            ),
        );
    }

    getPropertySources(): Observable<PropertySource[]> {
        return this.http.get<Env>('management/env').pipe(map((env) => env.propertySources));
    }
}
