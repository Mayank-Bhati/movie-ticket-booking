package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.mayankbhati.movietickets.notification.domain.OutboxEvent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
public class OutboxStore {
    @PersistenceContext
    private EntityManager entityManager;

    public void save(OutboxEvent event) { entityManager.persist(event); }

    public boolean exists(String dedupeKey) {
        return entityManager.createQuery("""
                select count(e) from OutboxEvent e where e.dedupeKey = :key
                """, Long.class).setParameter("key", dedupeKey).getSingleResult() > 0;
    }

    public List<OutboxEvent> lockPendingBatch(OffsetDateTime now, int size) {
        return entityManager.createQuery("""
                select e from OutboxEvent e
                where e.status = 'PENDING' and e.availableAt <= :now order by e.id
                """, OutboxEvent.class).setParameter("now", now).setMaxResults(size)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }
}
