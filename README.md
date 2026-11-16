# PR 14645 real-browser evidence

Six screenshots of the running Artemis production client with real stored course,
message and lecture data. Real application themes were selected with Artemis
controls. No routes, components, theme colours or captures were mocked/recoloured.

The client was built from 3ce3ae2693 plus the exact recipient race/IME/Escape code
committed as afd199eb8c9fc177e6e907242abd1593ab898f3a. The footer therefore shows
3ce3ae2. Both themes passed recipient keyboard/composition handling, persisted
multiline forwarding, actual delayed user-search responses, empty-result Escape
propagation, and the full PDF focus/navigation flow with no browser page errors.

Only six PNGs and this README are published. No authentication state, traces,
scripts or local debug files are included. OS-native IME, actual Safari, live
build-agent jobs, Iris/online states and remote all-role checks remain pending.
