# API Reference

Base URL: `http://localhost:8080/api/v1`

Authentication uses HTTP Basic. Browse and customer registration endpoints are public. Booking
endpoints require a `CUSTOMER`; `/admin/*` requires an `ADMIN`.

## Public

| Method | Path | Purpose |
|---|---|---|
| POST | `/customers` | Register a customer |
| GET | `/cities` | List supported cities |
| GET | `/movies` | List movies |
| GET | `/showings?cityId=&movieId=` | Find future showings |
| GET | `/showings/{id}/seats` | View labels, prices, and live availability |

## Customer

| Method | Path | Purpose |
|---|---|---|
| POST | `/holds` | Hold 1-10 show-seat IDs |
| POST | `/bookings` | Convert a hold; requires `Idempotency-Key` |
| GET | `/bookings` | Booking history |
| GET | `/bookings/{id}` | Booking detail |
| DELETE | `/bookings/{id}` | Cancel and calculate refund |

## Admin

| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/cities` | Create city and timezone |
| POST | `/admin/theaters` | Create theater |
| POST | `/admin/screens` | Create screen and generated seat layout |
| POST | `/admin/movies` | Create movie |
| PUT | `/admin/pricing-tiers/{category}` | Set regular/premium multiplier |
| POST | `/admin/refund-policies` | Create time-tiered policy |
| POST | `/admin/showings` | Schedule show and materialize priced seats |
| POST | `/admin/discount-codes` | Create percentage/fixed code |

## Error Contract

Application errors return a stable code and HTTP status:

```json
{
  "timestamp": "2026-09-22T10:00:00Z",
  "status": 409,
  "code": "SEAT_UNAVAILABLE",
  "message": "Seats are no longer available: [A1]",
  "path": "/api/v1/holds"
}
```

Important statuses: `400` validation/business input, `401` missing/invalid credentials, `403` wrong
role, `404` hidden/missing resource, `409` state conflict, and `410` expired hold.

