# PR 14645 real-browser evidence

The six PNG files show the actual running Artemis production client with real,
stored course, channel, message and lecture data. Themes were selected through
Artemis controls. No routes, components, theme colours or captures were mocked,
overridden or recoloured.

These captures include the direct native-input keyboard binding committed as
3ce3ae269395c4ed9925079f3a3475af385640ad. The footer says 3e3b7cd because the
production build was made from that parent plus the exact pending template edit,
before committing it. Both actual themes passed Arrow navigation, composing Enter,
Escape, recipient selection, persisted multiline forwarding, and PDF initial focus,
Tab trapping, disappearing-button boundaries, reverse arrows, Escape and restored
trigger focus. Browser page-error lists are empty.

Only these six screenshots and this README are published. Authentication state,
traces, scripts and local debugging files are not included. Native OS IME input,
live build-agent jobs, Iris/online states and remote user-role testing remain pending.
