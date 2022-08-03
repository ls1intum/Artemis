import 'zone.js';
import '@angular/localize/init';
import * as process from 'process';

// Fix needed for SockJS, see https://github.com/sockjs/sockjs-client/issues/439
(window as any).global = window;
// Fix needed for Buffer, see https://stackoverflow.com/questions/50371593/angular-6-uncaught-referenceerror-buffer-is-not-defined
global.Buffer = global.Buffer || require('buffer').Buffer;

window['process'] = process;
