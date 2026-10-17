package com.fintechdemo.identity.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID customerId, String phoneNumber, String email, String firstName, String lastName, String kycTier, Instant createdAt) {

}
