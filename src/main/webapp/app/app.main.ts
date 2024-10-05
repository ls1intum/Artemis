import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ProdConfig } from './core/config/prod.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { bootstrapApplication } from '@angular/platform-browser';

ProdConfig();
MonacoConfig();

bootstrapApplication(JhiMainComponent).catch((err) => console.error(err));
