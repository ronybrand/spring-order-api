package br.com.ronybrand.orderapi;

import br.com.ronybrand.orderapi.commons.config.SpringDocRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ImportRuntimeHints(SpringDocRuntimeHints.class)
public class SpringOrderApiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SpringOrderApiApplication.class, args);
    }
}
