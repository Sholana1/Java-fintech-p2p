package com.fintechdemo.identity.application.port.in;

public record RegisterCustomerCommand(String phoneNumber, String email, String firstName, String lastName, String password) {
    @Override
    public String toString() {
        return "RegisterCustomerCommand[phoneNumber=***, email=***, password=***]";
    }
}
