package com.fintechdemo.identity.application.service;

import com.fintechdemo.identity.application.port.in.RegisterCustomerCommand;
import com.fintechdemo.identity.application.port.in.RegisterCustomerUseCase;
import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.application.port.out.PasswordHasherPort;
import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.EmailAddress;
import com.fintechdemo.identity.domain.model.PasswordHash;
import com.fintechdemo.identity.domain.model.PasswordPolicy;
import com.fintechdemo.identity.domain.model.PhoneNumber;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class RegisterCustomerService implements RegisterCustomerUseCase {

    private final CustomerPort customers;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public RegisterCustomerService(CustomerPort customers, PasswordHasherPort passwordHasher, Clock clock) {
        this.customers = customers;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    public Customer register(RegisterCustomerCommand command) {
        PhoneNumber phoneNumber = new PhoneNumber(command.phoneNumber());
        EmailAddress email = new EmailAddress(command.email());
        PasswordPolicy.check(command.password());

        PasswordHash passwordHash = passwordHasher.hash(command.password());
        Customer customer = Customer.register(phoneNumber, email, command.firstName(), command.lastName(),
            passwordHash, clock.instant());

        customers.add(customer);
        return customer;
    }
}
