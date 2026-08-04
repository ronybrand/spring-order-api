package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Exercises the real {@code logback.xml} CONSOLE appender pattern (not a mock) to pin the
 * scrubbing behaviour it exists for: known sensitive-data shapes must never reach the log output,
 * whether they arrive via the log message or via an arbitrary exception's stack trace - the exact
 * path {@code GlobalExceptionHandler#handleUnexpected} takes for a caught {@code Exception} whose
 * message content this codebase does not control.
 */
class LogScrubbingTest {

    private final PatternLayout layout = consoleLayout();

    @Test
    void doLayout_ShouldRedactEmail_InLogMessage() {
        final LoggingEvent event = event("Customer contact: john.doe@example.com", null);

        assertThat(layout.doLayout(event))
                .doesNotContain("john.doe@example.com")
                .contains("***REDACTED-EMAIL***");
    }

    @Test
    void doLayout_ShouldRedactCpf_InLogMessage() {
        final LoggingEvent event = event("Tax id conflict: 123.456.789-09", null);

        assertThat(layout.doLayout(event))
                .doesNotContain("123.456.789-09")
                .contains("***REDACTED-CPF***");
    }

    @Test
    void doLayout_ShouldRedactJwt_InExceptionStackTrace() {
        final String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dGhpc2lzYXNpZ25hdHVyZQ";
        final RuntimeException ex = new RuntimeException("Unexpected error with token " + token);

        final LoggingEvent event = event("Unexpected error", ex);

        assertThat(layout.doLayout(event))
                .doesNotContain(token)
                .contains("***REDACTED-TOKEN***");
    }

    private LoggingEvent event(final String message, final Throwable throwable) {
        final LoggingEvent event = new LoggingEvent();
        event.setLoggerName(getClass().getName());
        event.setLevel(Level.ERROR);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        if (throwable != null) {
            event.setThrowableProxy(new ThrowableProxy(throwable));
        }
        return event;
    }

    private PatternLayout consoleLayout() {
        final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        final Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender = root.getAppender("CONSOLE");
        final ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> consoleAppender =
                (ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent>) appender;
        final PatternLayoutEncoder encoder = (PatternLayoutEncoder) consoleAppender.getEncoder();
        return (PatternLayout) encoder.getLayout();
    }
}
