# ADR 0001: Feature-Oriented Modular Monolith

Status: Accepted

## Decision

Use one Spring Boot process and one relational database. Package code first by business capability
(`identity`, `catalog`, `booking`, `notification`) and then by API/application/infrastructure role.

## Rationale

The core challenge is transactional consistency around seats, payment, and refunds. A modular
monolith keeps that consistency explicit and testable while avoiding distributed transaction and
deployment concerns that are out of scope. Feature boundaries keep future extraction possible.

