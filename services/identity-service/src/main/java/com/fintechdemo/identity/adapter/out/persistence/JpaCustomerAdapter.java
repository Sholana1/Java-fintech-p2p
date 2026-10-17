package com.fintechdemo.identity.adapter.out.persistence;

import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.domain.exception.CustomerAlreadyExistsException;
import com.fintechdemo.identity.domain.model.Customer;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * CustomerPort backed by PostgreSQL via JPA.
 * No "SELECT ... WHERE phone = ?" check first: two registrations racing would both pass it.
 * The UNIQUE constraints decide, atomically; we translate their violation into a domain exception.
 */
@Component
class JpaCustomerAdapter implements CustomerPort {

    // PostgreSQL error code for unique_violation (Appendix A of the PostgreSQL docs).
    private static final String UNIQUE_VIOLATION = "23505";

    private final CustomerJpaRepository repository;

    JpaCustomerAdapter(CustomerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(Customer customer) {
        try {
            // saveAndFlush: run the INSERT now, inside this try, not later at commit time.
            repository.saveAndFlush(CustomerPersistenceMapper.toEntity(customer));
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueViolation(violation)) {
                throw new CustomerAlreadyExistsException();
            }
            throw violation; // NOT NULL or CHECK failed: that is a bug, not a duplicate.
        }
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException violation) {
        return violation.getMostSpecificCause() instanceof SQLException sql
            && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
