package com.mayankbhati.movietickets.booking.infrastructure;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.mayankbhati.movietickets.booking.domain.Booking;
import com.mayankbhati.movietickets.booking.domain.Payment;
import com.mayankbhati.movietickets.booking.domain.Refund;
import com.mayankbhati.movietickets.booking.domain.SeatHold;
import com.mayankbhati.movietickets.catalog.domain.DiscountCode;
import com.mayankbhati.movietickets.catalog.domain.ShowSeat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
public class BookingStore {
    @PersistenceContext
    private EntityManager entityManager;

    public List<ShowSeat> lockSeats(long showingId, Collection<Long> seatIds) {
        return entityManager.createQuery("""
                select ss from ShowSeat ss join fetch ss.seat
                where ss.showing.id = :showingId and ss.id in :seatIds order by ss.id
                """, ShowSeat.class).setParameter("showingId", showingId).setParameter("seatIds", seatIds)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    public List<ShowSeat> lockSeatsForHold(String holdId) {
        return entityManager.createQuery("""
                select ss from ShowSeat ss join fetch ss.seat
                where ss.holdId = :holdId order by ss.id
                """, ShowSeat.class).setParameter("holdId", holdId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    public List<ShowSeat> lockSeatsForBooking(String bookingId) {
        return entityManager.createQuery("""
                select ss from ShowSeat ss where ss.bookingId = :bookingId order by ss.id
                """, ShowSeat.class).setParameter("bookingId", bookingId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    public Optional<SeatHold> lockHold(String holdId) {
        return entityManager.createQuery("""
                select h from SeatHold h join fetch h.showing where h.id = :id
                """, SeatHold.class).setParameter("id", holdId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
    }

    public Optional<DiscountCode> lockDiscount(String code) {
        return Optional.ofNullable(entityManager.find(DiscountCode.class, code, LockModeType.PESSIMISTIC_WRITE));
    }

    public Optional<Booking> byIdempotencyKey(long customerId, String key) {
        return entityManager.createQuery("""
                select b from Booking b where b.customerId = :customerId and b.idempotencyKey = :key
                """, Booking.class).setParameter("customerId", customerId).setParameter("key", key)
                .getResultStream().findFirst();
    }

    public Optional<Booking> booking(long customerId, String bookingId) {
        return entityManager.createQuery("""
                select distinct b from Booking b
                join fetch b.showing sh join fetch sh.movie left join fetch b.items
                where b.id = :id and b.customerId = :customerId
                """, Booking.class).setParameter("id", bookingId).setParameter("customerId", customerId)
                .getResultStream().findFirst();
    }

    public Optional<Booking> lockBooking(String bookingId) {
        return entityManager.createQuery("""
                select b from Booking b join fetch b.showing sh join fetch sh.refundPolicy
                where b.id = :id
                """, Booking.class).setParameter("id", bookingId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
    }

    public List<Booking> history(long customerId) {
        return entityManager.createQuery("""
                select b from Booking b join fetch b.showing sh join fetch sh.movie
                where b.customerId = :customerId order by b.createdAt desc
                """, Booking.class).setParameter("customerId", customerId).getResultList();
    }

    public List<Reminder> upcomingReminders(OffsetDateTime from, OffsetDateTime to) {
        List<Object[]> rows = entityManager.createQuery("""
                select b.id, u.email, m.title, sh.startsAt
                from Booking b join b.showing sh join sh.movie m, UserAccount u
                where u.id = b.customerId and b.status = 'CONFIRMED'
                  and sh.startsAt >= :from and sh.startsAt < :to
                """, Object[].class).setParameter("from", from).setParameter("to", to).getResultList();
        return rows.stream().map(row -> new Reminder((String) row[0], (String) row[1],
                (String) row[2], (OffsetDateTime) row[3])).toList();
    }

    public Payment payment(String bookingId) {
        return entityManager.createQuery("select p from Payment p where p.booking.id = :id", Payment.class)
                .setParameter("id", bookingId).getSingleResult();
    }

    public int refundPercent(long policyId, long minutesBefore) {
        return entityManager.createQuery("""
                select r.refundPercent from RefundRule r
                where r.policy.id = :policyId and r.minimumMinutesBefore <= :minutes
                order by r.minimumMinutesBefore desc
                """, Integer.class).setParameter("policyId", policyId).setParameter("minutes", minutesBefore)
                .setMaxResults(1).getResultStream().findFirst().orElse(0);
    }

    public int releaseExpired(OffsetDateTime now) {
        entityManager.createQuery("""
                update SeatHold h set h.status = 'EXPIRED'
                where h.status = 'ACTIVE' and h.expiresAt <= :now
                """).setParameter("now", now).executeUpdate();
        int released = entityManager.createQuery("""
                update ShowSeat ss set ss.status = 'AVAILABLE', ss.holdId = null,
                    ss.holdExpiresAt = null, ss.version = ss.version + 1
                where ss.status = 'HELD' and ss.holdExpiresAt <= :now
                """).setParameter("now", now).executeUpdate();
        entityManager.clear();
        return released;
    }

    public void persist(Object entity) { entityManager.persist(entity); }
    public void persistAll(Iterable<?> entities) { entities.forEach(entityManager::persist); }
    public void persistRefund(Refund refund) { entityManager.persist(refund); }

    public record Reminder(String bookingId, String email, String movieTitle, OffsetDateTime startsAt) { }
}
