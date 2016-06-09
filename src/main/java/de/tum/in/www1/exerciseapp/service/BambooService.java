package de.tum.in.www1.exerciseapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BambooService {

    private final Logger log = LoggerFactory.getLogger(BambooService.class);

}
