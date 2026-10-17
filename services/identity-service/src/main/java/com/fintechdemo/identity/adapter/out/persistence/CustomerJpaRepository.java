package com.fintechdemo.identity.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation at startup. Login (L04 Part B) adds findByPhoneNumber here. */
interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {
}
