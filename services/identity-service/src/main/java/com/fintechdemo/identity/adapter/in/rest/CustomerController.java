package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.application.port.in.RegisterCustomerUseCase;
import com.fintechdemo.identity.domain.model.Customer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final RegisterCustomerUseCase registerCustomer;

    public CustomerController(RegisterCustomerUseCase registerCustomer) {
        this.registerCustomer = registerCustomer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer = registerCustomer.register(CustomerRestMapper.toCommand(request));
        return CustomerRestMapper.toResponse(customer);
    }
}
