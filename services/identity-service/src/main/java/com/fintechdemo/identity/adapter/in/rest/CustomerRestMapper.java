package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.application.port.in.RegisterCustomerCommand;
import com.fintechdemo.identity.domain.model.Customer;

public class CustomerRestMapper {
    private CustomerRestMapper() {}

    static RegisterCustomerCommand toCommand(RegisterCustomerRequest request) {
        return new RegisterCustomerCommand(request.phoneNumber(), request.email(), request.firstName(), request.lastName(), request.password());
    }

    static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
            customer.id().value(),
            customer.phoneNumber().masked(),
            customer.email().value(),
            customer.firstName(),
            customer.lastName(),
            customer.kycTier().name(),
            customer.createdAt()
        );
    }
}
