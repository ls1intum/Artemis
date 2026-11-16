# PR 14645 browser evidence

Actual screenshots of the running Artemis production client at localhost:18080,
using an isolated database with realistic stored course/messages and a three-page
lecture handout. Both themes were selected through Artemis controls. No routes,
rendered components or theme colours were mocked or recoloured.

The instance was built from 6ebb29fd823148b6a319f3b6914d02cc1ba63de5.
PR head deb9d40f2ad5a3fceac5d4bb66248b15cd455a45 only adds regression tests;
its production client/server source is identical to the captured build.

The six PNG files are the only uploaded application artifacts. Authentication
state, traces and local debugging files are deliberately excluded.

Verified: recipient keyboard navigation, composing Enter, Escape and persistent
multiline forwarding; PDF initial focus and Tab trap, first/last-page navigation
focus, continued arrow handling, Escape and restored trigger focus. No browser
page errors. OS-native IME input and live build-agent jobs were not exercised.
