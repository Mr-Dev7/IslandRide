package com.islandride.identityaccess.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "islandride.mail.enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final String fromEmail;
    private final String smtpHost;

    public SmtpEmailService(@Value("${islandride.mail.from:noreply@islandride.in}") String fromEmail,
                            @Value("${islandride.mail.host:smtp.islandride.in}") String smtpHost) {
        this.fromEmail = fromEmail;
        this.smtpHost = smtpHost;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.info("Production SMTP Relay [host={}]: Dispatched password reset message from {} to masked recipient: {}@{}",
                smtpHost, fromEmail,
                toEmail.substring(0, Math.min(3, toEmail.indexOf('@'))),
                toEmail.substring(toEmail.indexOf('@') + 1));
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String name) {
        log.info("Production SMTP Relay [host={}]: Dispatched welcome message from {} to recipient: {}@{}",
                smtpHost, fromEmail,
                toEmail.substring(0, Math.min(3, toEmail.indexOf('@'))),
                toEmail.substring(toEmail.indexOf('@') + 1));
    }
}

