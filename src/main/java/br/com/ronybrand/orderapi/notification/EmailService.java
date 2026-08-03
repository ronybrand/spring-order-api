package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String TEMPLATE = "email/order-status-changed";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties notificationProperties;

    /**
     * @throws EmailSendingException on any failure to render or send the email - including
     *      Thymeleaf template errors, not just SMTP/messaging failures - so the caller always sees
     *      a single, retryable-classified exception type (DOMAIN.md §5).
     */
    void sendOrderStatusEmail(final OrderStatusChangedEvent event) {
        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(notificationProperties.fromAddress());
            helper.setTo(event.customerEmail());
            helper.setSubject("Order status updated: " + event.newStatus());

            final Context context = new Context();
            context.setVariable("event", event);
            helper.setText(templateEngine.process(TEMPLATE, context), true);

            mailSender.send(message);
        } catch (final MessagingException | MailException | TemplateEngineException e) {
            throw new EmailSendingException("Failed to send order status email", e);
        }
    }
}
