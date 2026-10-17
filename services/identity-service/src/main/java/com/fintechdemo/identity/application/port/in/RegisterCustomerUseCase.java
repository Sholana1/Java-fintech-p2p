package com.fintechdemo.identity.application.port.in;

import com.fintechdemo.identity.domain.model.Customer;

public interface RegisterCustomerUseCase {
    Customer register(RegisterCustomerCommand command);
}
