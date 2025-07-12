export default function videojs(...args: any[]) {
    return {
        ready: (cb: () => void) => cb(),
        dispose: () => {},
        on: () => {},
        off: () => {},
        src: () => {},
        currentTime: () => 0,
        autoplay: () => false,
        play: () => {},
        pause: () => {},
        addClass: () => {},
        removeClass: () => {},
    };
}
