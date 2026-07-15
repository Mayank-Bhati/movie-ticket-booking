-- Core schema: users, catalog (cities/theaters/screens/seats/movies/shows),
-- pricing, discounts, holds, bookings, payments, refunds, notifications.

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE cities (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    CONSTRAINT uq_cities_name UNIQUE (name)
);

CREATE TABLE theaters (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    city_id  BIGINT       NOT NULL,
    name     VARCHAR(150) NOT NULL,
    address  VARCHAR(255),
    CONSTRAINT fk_theaters_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT uq_theaters_city_name UNIQUE (city_id, name)
);

CREATE TABLE screens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    theater_id  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    CONSTRAINT fk_screens_theater FOREIGN KEY (theater_id) REFERENCES theaters (id),
    CONSTRAINT uq_screens_theater_name UNIQUE (theater_id, name)
);

CREATE TABLE seats (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    screen_id    BIGINT      NOT NULL,
    row_label    VARCHAR(5)  NOT NULL,
    seat_number  INT         NOT NULL,
    seat_type    VARCHAR(20) NOT NULL,
    CONSTRAINT fk_seats_screen FOREIGN KEY (screen_id) REFERENCES screens (id),
    CONSTRAINT uq_seats_position UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE movies (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(2000),
    language          VARCHAR(50),
    genre             VARCHAR(50),
    duration_minutes  INT          NOT NULL,
    rating            VARCHAR(10)
);

CREATE TABLE shows (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id    BIGINT        NOT NULL,
    screen_id   BIGINT        NOT NULL,
    starts_at   DATETIME      NOT NULL,
    ends_at     DATETIME      NOT NULL,
    base_price  DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_shows_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_shows_screen FOREIGN KEY (screen_id) REFERENCES screens (id)
);

CREATE INDEX idx_shows_screen_start ON shows (screen_id, starts_at);

CREATE TABLE pricing_tiers (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    description  VARCHAR(255),
    multiplier   DECIMAL(5,2) NOT NULL,
    CONSTRAINT uq_pricing_tiers_code UNIQUE (code)
);

CREATE TABLE discount_codes (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(50)   NOT NULL,
    type         VARCHAR(10)   NOT NULL,
    amount       DECIMAL(10,2) NOT NULL,
    min_amount   DECIMAL(10,2),
    max_uses     INT,
    used_count   INT           NOT NULL DEFAULT 0,
    valid_from   DATETIME,
    valid_until  DATETIME,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_discount_codes_code UNIQUE (code)
);

CREATE TABLE seat_holds (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    show_id     BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    expires_at  DATETIME    NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seat_holds_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_seat_holds_show FOREIGN KEY (show_id) REFERENCES shows (id)
);

CREATE INDEX idx_seat_holds_status_expiry ON seat_holds (status, expires_at);

CREATE TABLE show_seats (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id  BIGINT        NOT NULL,
    seat_id  BIGINT        NOT NULL,
    status   VARCHAR(20)   NOT NULL,
    price    DECIMAL(10,2) NOT NULL,
    hold_id  BIGINT,
    CONSTRAINT fk_show_seats_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_show_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT fk_show_seats_hold FOREIGN KEY (hold_id) REFERENCES seat_holds (id),
    CONSTRAINT uq_show_seats_show_seat UNIQUE (show_id, seat_id)
);

CREATE TABLE bookings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_ref       VARCHAR(20)   NOT NULL,
    user_id           BIGINT        NOT NULL,
    show_id           BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    subtotal          DECIMAL(10,2) NOT NULL,
    discount_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount      DECIMAL(10,2) NOT NULL,
    discount_code_id  BIGINT,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at      DATETIME,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_bookings_discount FOREIGN KEY (discount_code_id) REFERENCES discount_codes (id),
    CONSTRAINT uq_bookings_ref UNIQUE (booking_ref)
);

CREATE INDEX idx_bookings_user ON bookings (user_id);

CREATE TABLE booking_seats (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id    BIGINT        NOT NULL,
    show_seat_id  BIGINT        NOT NULL,
    price         DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_show_seat FOREIGN KEY (show_seat_id) REFERENCES show_seats (id)
);

CREATE TABLE payments (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id       BIGINT        NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    transaction_ref  VARCHAR(50),
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

CREATE TABLE refund_rules (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    min_hours_before_show  INT NOT NULL,
    refund_percent         INT NOT NULL,
    CONSTRAINT uq_refund_rules_hours UNIQUE (min_hours_before_show)
);

CREATE TABLE refunds (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id      BIGINT        NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    refund_percent  INT           NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refunds_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

CREATE TABLE notifications (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    booking_id  BIGINT,
    type        VARCHAR(30)   NOT NULL,
    payload     VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at     DATETIME,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

CREATE INDEX idx_notifications_status ON notifications (status);
