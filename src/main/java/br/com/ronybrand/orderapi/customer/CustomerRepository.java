package br.com.ronybrand.orderapi.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    boolean existsByTaxId(String taxId);

    boolean existsByPassportNumber(String passportNumber);

    boolean existsByTaxIdAndIdNot(String taxId, UUID id);

    boolean existsByPassportNumberAndIdNot(String passportNumber, UUID id);
}
