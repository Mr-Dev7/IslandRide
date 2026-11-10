package com.islandride.identityaccess.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "islandride.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        // Do not expose raw token values in logs according to security rules
        log.info("Dispatched password reset notification to masked recipient: {}@{}", 
                toEmail.substring(0, Math.min(3, toEmail.indexOf('@'))),
                toEmail.substring(toEmail.indexOf('@') + 1));
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String name) {
        log.info("Dispatched welcome email to recipient: {}@{}", 
                toEmail.substring(0, Math.min(3, toEmail.indexOf('@'))),
                toEmail.substring(toEmail.indexOf('@') + 1));
    }
}

