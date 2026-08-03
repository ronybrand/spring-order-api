package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateInputException;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final TemplateEngine templateEngine = mock(TemplateEngine.class);
    private final NotificationProperties notificationProperties = new NotificationProperties("no-reply@order-api.dev");
    private final EmailService emailService = new EmailService(mailSender, templateEngine, notificationProperties);

    @Test
    void sendOrderStatusEmail_ShouldThrowEmailSendingException_WhenSmtpSendFails() {
        final MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));
        final OrderStatusChangedEvent event = new OrderStatusChangedEvent(UUID.randomUUID(), "ada@example.com", "Ada Lovelace",
                OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"), LocalDateTime.now());

        assertThatThrownBy(() -> emailService.sendOrderStatusEmail(event)).isInstanceOf(EmailSendingException.class);
    }

    @Test
    void sendOrderStatusEmail_ShouldThrowEmailSendingException_WhenTemplateRenderingFails() {
        final MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any())).thenThrow(new TemplateInputException("Template not found"));
        final OrderStatusChangedEvent event = new OrderStatusChangedEvent(UUID.randomUUID(), "ada@example.com", "Ada Lovelace",
                OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"), LocalDateTime.now());

        assertThatThrownBy(() -> emailService.sendOrderStatusEmail(event)).isInstanceOf(EmailSendingException.class);
    }
}
