package com.moviebooking.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moviebooking.support.IntegrationTest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BookingFlowIntegrationTest extends IntegrationTest {

    @Test
    void customerCanBrowseHoldBookAndCancel() throws Exception {
        String admin = adminToken();
        long showId = seedShow(admin);

        String customer = registerCustomer("moviegoer@example.com");

        // Browse the seat map: all seats available.
        String seatMap = mockMvc.perform(get("/api/shows/" + showId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        int firstSeatId = JsonPath.read(seatMap, "$[0].showSeatId");
        int secondSeatId = JsonPath.read(seatMap, "$[1].showSeatId");

        // Hold two seats.
        String hold = mockMvc.perform(authed(post("/api/holds"), customer)
                        .content("{\"showId\":" + showId + ",\"showSeatIds\":[" + firstSeatId + "," + secondSeatId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.subtotal").value(400.0))
                .andReturn().getResponse().getContentAsString();
        int holdId = JsonPath.read(hold, "$.holdId");

        // Book the hold.
        String booking = mockMvc.perform(authed(post("/api/bookings"), customer)
                        .content("{\"holdId\":" + holdId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.totalAmount").value(400.0))
                .andReturn().getResponse().getContentAsString();
        String bookingRef = JsonPath.read(booking, "$.bookingRef");
        int bookingId = JsonPath.read(booking, "$.id");

        // Seats now booked.
        mockMvc.perform(get("/api/shows/" + showId + "/seats"))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));

        // History shows the confirmed booking.
        mockMvc.perform(authed(get("/api/bookings"), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingRef").value(bookingRef));

        // A confirmation notification was recorded.
        mockMvc.perform(authed(get("/api/notifications"), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMED"));

        // Cancel: full refund since the show is days away.
        mockMvc.perform(authed(delete("/api/bookings/" + bookingId), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundPercent").value(100));

        // Seats freed again.
        mockMvc.perform(get("/api/shows/" + showId + "/seats"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void bookingAnotherUsersHoldIsForbidden() throws Exception {
        String admin = adminToken();
        long showId = seedShow(admin);
        String owner = registerCustomer("owner@example.com");
        String attacker = registerCustomer("attacker@example.com");

        String seatMap = mockMvc.perform(get("/api/shows/" + showId + "/seats"))
                .andReturn().getResponse().getContentAsString();
        int seatId = JsonPath.read(seatMap, "$[0].showSeatId");

        String hold = mockMvc.perform(authed(post("/api/holds"), owner)
                        .content("{\"showId\":" + showId + ",\"showSeatIds\":[" + seatId + "]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int holdId = JsonPath.read(hold, "$.holdId");

        mockMvc.perform(authed(post("/api/bookings"), attacker)
                        .content("{\"holdId\":" + holdId + "}"))
                .andExpect(status().isForbidden());
    }

    // ----- helpers -----

    private long seedShow(String adminToken) throws Exception {
        long cityId = createId(post("/api/admin/cities"), adminToken, "{\"name\":\"Bangalore\"}");
        long theaterId = createId(post("/api/admin/theaters"), adminToken,
                "{\"cityId\":" + cityId + ",\"name\":\"PVR\",\"address\":\"MG Road\"}");
        long screenId = createId(post("/api/admin/screens"), adminToken,
                "{\"theaterId\":" + theaterId + ",\"name\":\"S1\",\"rows\":[{\"rowLabel\":\"A\",\"seatType\":\"REGULAR\",\"seatCount\":5}]}");
        long movieId = createId(post("/api/admin/movies"), adminToken,
                "{\"title\":\"Dune\",\"durationMinutes\":155}");
        LocalDateTime start = nextWeekdayAtLeastThreeDaysAhead();
        return createId(post("/api/admin/shows"), adminToken,
                "{\"movieId\":" + movieId + ",\"screenId\":" + screenId + ",\"startsAt\":\"" + start
                        + "\",\"endsAt\":\"" + start.plusHours(2) + "\",\"basePrice\":200}");
    }

    /** A weekday show at least 3 days out: deterministic weekday pricing and a full-refund window. */
    private LocalDateTime nextWeekdayAtLeastThreeDaysAhead() {
        LocalDate date = LocalDate.now().plusDays(3);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(18, 0);
    }
}
