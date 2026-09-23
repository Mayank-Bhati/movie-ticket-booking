package com.mayankbhati.movietickets.catalog.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.mayankbhati.movietickets.catalog.domain.City;
import com.mayankbhati.movietickets.catalog.domain.DiscountCode;
import com.mayankbhati.movietickets.catalog.domain.Movie;
import com.mayankbhati.movietickets.catalog.domain.PricingTier;
import com.mayankbhati.movietickets.catalog.domain.RefundPolicy;
import com.mayankbhati.movietickets.catalog.domain.Screen;
import com.mayankbhati.movietickets.catalog.domain.Seat;
import com.mayankbhati.movietickets.catalog.domain.ShowSeat;
import com.mayankbhati.movietickets.catalog.domain.Showing;
import com.mayankbhati.movietickets.catalog.domain.Theater;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class CatalogStore {
    @PersistenceContext
    private EntityManager entityManager;

    public List<City> cities() {
        return entityManager.createQuery("select c from City c order by c.name", City.class).getResultList();
    }

    public List<Movie> movies() {
        return entityManager.createQuery("select m from Movie m order by m.title", Movie.class).getResultList();
    }

    public List<ShowingListing> showings(Long cityId, Long movieId, OffsetDateTime now) {
        String jpql = """
                select new com.mayankbhati.movietickets.catalog.infrastructure.CatalogStore$ShowingListing(
                    sh.id, m.id, m.title, c.id, c.name, t.name, sc.name, sh.startsAt, min(ss.priceCents))
                from Showing sh
                join sh.movie m join sh.screen sc join sc.theater t join t.city c, ShowSeat ss
                where ss.showing = sh and sh.status = 'SCHEDULED' and sh.startsAt > :now
                  and (:cityId is null or c.id = :cityId)
                  and (:movieId is null or m.id = :movieId)
                group by sh.id, m.id, m.title, c.id, c.name, t.name, sc.name, sh.startsAt
                order by sh.startsAt
                """;
        return entityManager.createQuery(jpql, ShowingListing.class)
                .setParameter("now", now).setParameter("cityId", cityId).setParameter("movieId", movieId)
                .getResultList();
    }

    public List<ShowSeat> seats(long showingId) {
        return entityManager.createQuery("""
                select ss from ShowSeat ss join fetch ss.seat s
                where ss.showing.id = :showingId order by s.rowLabel, s.seatNumber
                """, ShowSeat.class).setParameter("showingId", showingId).getResultList();
    }

    public List<Seat> screenSeats(long screenId) {
        return entityManager.createQuery("select s from Seat s where s.screen.id = :id order by s.id", Seat.class)
                .setParameter("id", screenId).getResultList();
    }

    public List<Showing> scheduledShowings(long screenId) {
        return entityManager.createQuery("""
                select sh from Showing sh join fetch sh.movie
                where sh.screen.id = :id and sh.status = 'SCHEDULED'
                """, Showing.class).setParameter("id", screenId).getResultList();
    }

    public City city(long id) { return find(City.class, id); }
    public Theater theater(long id) { return find(Theater.class, id); }
    public Screen screen(long id) { return find(Screen.class, id); }
    public Movie movie(long id) { return find(Movie.class, id); }
    public RefundPolicy refundPolicy(long id) { return find(RefundPolicy.class, id); }
    public Showing showing(long id) { return find(Showing.class, id); }
    public PricingTier pricingTier(String category) { return entityManager.find(PricingTier.class, category); }

    public <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    public void persistAll(Iterable<?> entities) { entities.forEach(entityManager::persist); }

    public DiscountCode discount(String code) { return entityManager.find(DiscountCode.class, code); }

    private <T> T find(Class<T> type, long id) { return entityManager.find(type, id); }

    public record ShowingListing(long id, long movieId, String movieTitle, long cityId,
                                 String cityName, String theaterName, String screenName,
                                 OffsetDateTime startsAt, long priceFromCents) { }
}
