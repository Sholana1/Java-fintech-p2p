package com.fintechdemo.identity.adapter.in.rest;
import jakarta.validation.constraints.NotBlank;

public record RegisterCustomerRequest(@NotBlank String phoneNumber,@NotBlank String email,@NotBlank String firstName,@NotBlank String lastName,@NotBlank String password) {
    @Override
    public String toString() {
        return "RegisterCustomerRequest[phoneNumber=***, email=***, password=***]";
    }
}
