import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { Bean, Beans, ConfigProps, Env, PropertySource } from './configuration.model';
import { SERVER_API_URL } from 'app/environments/environment';

@Injectable({ providedIn: 'root' })
export class ConfigurationService {
    constructor(private http: HttpClient) {}

    getBeans(): Observable<Bean[]> {
        return this.http.get<ConfigProps>(SERVER_API_URL + 'management/configprops').pipe(
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
        return this.http.get<Env>(SERVER_API_URL + 'management/env').pipe(map((env) => env.propertySources));
    }
}
