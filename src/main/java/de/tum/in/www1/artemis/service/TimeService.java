package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

@Service
public class TimeService {

    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }
}
