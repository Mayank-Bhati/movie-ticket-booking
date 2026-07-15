package com.moviebooking.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviebooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CatalogAdminIntegrationTest extends IntegrationTest {

    @Test
    void cityCrudAndBrowse() throws Exception {
        String admin = adminToken();
        long cityId = createId(post("/api/admin/cities"), admin, "{\"name\":\"Chennai\"}");

        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Chennai')]").exists());

        mockMvc.perform(authed(delete("/api/admin/cities/" + cityId), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateCityIsRejected() throws Exception {
        String admin = adminToken();
        createId(post("/api/admin/cities"), admin, "{\"name\":\"Hyderabad\"}");
        mockMvc.perform(authed(post("/api/admin/cities"), admin).content("{\"name\":\"hyderabad\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingUnknownCityReturnsNotFound() throws Exception {
        mockMvc.perform(authed(delete("/api/admin/cities/9999"), adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theaterAndScreenLayout() throws Exception {
        String admin = adminToken();
        long cityId = createId(post("/api/admin/cities"), admin, "{\"name\":\"Kolkata\"}");
        long theaterId = createId(post("/api/admin/theaters"), admin,
                "{\"cityId\":" + cityId + ",\"name\":\"INOX\",\"address\":\"Park St\"}");
        long screenId = createId(post("/api/admin/screens"), admin,
                "{\"theaterId\":" + theaterId + ",\"name\":\"Audi 1\",\"rows\":["
                        + "{\"rowLabel\":\"A\",\"seatType\":\"REGULAR\",\"seatCount\":4},"
                        + "{\"rowLabel\":\"B\",\"seatType\":\"PREMIUM\",\"seatCount\":2}]}");

        mockMvc.perform(authed(get("/api/admin/theaters?cityId=" + cityId), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("INOX"));

        mockMvc.perform(authed(get("/api/admin/screens/" + screenId), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatCount").value(6));

        mockMvc.perform(authed(get("/api/admin/screens?theaterId=" + theaterId), admin))
                .andExpect(jsonPath("$[0].seatCount").value(6));
    }

    @Test
    void theaterInUnknownCityReturnsNotFound() throws Exception {
        mockMvc.perform(authed(post("/api/admin/theaters"), adminToken())
                        .content("{\"cityId\":9999,\"name\":\"X\",\"address\":\"Y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void movieCrud() throws Exception {
        String admin = adminToken();
        long movieId = createId(post("/api/admin/movies"), admin,
                "{\"title\":\"Interstellar\",\"durationMinutes\":169,\"genre\":\"SciFi\"}");

        mockMvc.perform(authed(put("/api/admin/movies/" + movieId), admin)
                        .content("{\"title\":\"Interstellar (IMAX)\",\"durationMinutes\":169}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Interstellar (IMAX)"));

        mockMvc.perform(get("/api/movies/" + movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Interstellar (IMAX)"));

        mockMvc.perform(get("/api/movies"))
                .andExpect(jsonPath("$[?(@.id==" + movieId + ")]").exists());

        mockMvc.perform(authed(delete("/api/admin/movies/" + movieId), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/movies/" + movieId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatingUnknownMovieReturnsNotFound() throws Exception {
        mockMvc.perform(authed(put("/api/admin/movies/9999"), adminToken())
                        .content("{\"title\":\"X\",\"durationMinutes\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void showValidationErrors() throws Exception {
        String admin = adminToken();
        long cityId = createId(post("/api/admin/cities"), admin, "{\"name\":\"Delhi\"}");
        long theaterId = createId(post("/api/admin/theaters"), admin,
                "{\"cityId\":" + cityId + ",\"name\":\"PVR\",\"address\":\"CP\"}");
        long screenId = createId(post("/api/admin/screens"), admin,
                "{\"theaterId\":" + theaterId + ",\"name\":\"S1\",\"rows\":[{\"rowLabel\":\"A\",\"seatType\":\"REGULAR\",\"seatCount\":3}]}");
        long movieId = createId(post("/api/admin/movies"), admin, "{\"title\":\"Oppenheimer\",\"durationMinutes\":180}");

        // end before start
        mockMvc.perform(authed(post("/api/admin/shows"), admin)
                        .content("{\"movieId\":" + movieId + ",\"screenId\":" + screenId
                                + ",\"startsAt\":\"2027-01-01T20:00:00\",\"endsAt\":\"2027-01-01T18:00:00\",\"basePrice\":200}"))
                .andExpect(status().isBadRequest());

        // unknown screen
        mockMvc.perform(authed(post("/api/admin/shows"), admin)
                        .content("{\"movieId\":" + movieId + ",\"screenId\":9999"
                                + ",\"startsAt\":\"2027-01-01T18:00:00\",\"endsAt\":\"2027-01-01T20:00:00\",\"basePrice\":200}"))
                .andExpect(status().isNotFound());
    }
}
