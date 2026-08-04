package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.security.Sensitive;
import br.com.ronybrand.orderapi.commons.security.SensitiveDataMasker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identity by {@code taxId} (business key) - an editable field on {@code update}, so it is not a
 * stable collection key after an update (DOMAIN.md §2).
 */
@Getter
@Setter
@Builder
@Entity
@Table(name = "customer")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "taxId")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Sensitive
    @Column(name = "tax_id", nullable = false, unique = true)
    private String taxId;

    @Sensitive
    @Column(name = "passport_number", unique = true)
    private String passportNumber;

    @Sensitive
    @Column(nullable = false)
    private String email;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Override
    public String toString() {
        return SensitiveDataMasker.toString(this);
    }
}
