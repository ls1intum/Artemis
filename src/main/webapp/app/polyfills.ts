import 'zone.js';
import '@angular/localize/init';
import * as process from 'process';

// Fix needed for SockJS, see https://github.com/sockjs/sockjs-client/issues/439
(window as any).global = window;

window['process'] = process;
