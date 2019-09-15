package de.tum.in.www1.artemis.util;

import org.springframework.stereotype.Service;

@Service
public class TimeService {

    public long nanoSecondsToSeconds(long nanoSeconds) {
        return nanoSeconds / 1000000000;
    }

    public long secondsToNanoSeconds(long seconds) {
        return seconds * 1000000000;
    }

    public long nanoSecondsToMilliSeconds(long nanoSeconds) {
        return nanoSeconds / 1000000;
    }

    public long milliSecondsToNanoSeconds(long millSeconds) {
        return millSeconds * 1000000;
    }
}
