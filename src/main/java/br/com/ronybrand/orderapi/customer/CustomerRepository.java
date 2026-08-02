package br.com.ronybrand.orderapi.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByTaxId(String taxId);

    boolean existsByPassportNumber(String passportNumber);
}
