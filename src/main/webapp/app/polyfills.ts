import 'zone.js';

// Fix needed for SockJS, see https://github.com/sockjs/sockjs-client/issues/439
(window as any).global = window;
