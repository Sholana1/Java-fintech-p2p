package com.fintechdemo.identity.application.port.out;

import com.fintechdemo.identity.domain.model.Customer;

public interface CustomerPort {
    // Store a new customer. Throw CustomerAlreadyExistsException if the phone or email is taken
    void add(Customer customer);
}
