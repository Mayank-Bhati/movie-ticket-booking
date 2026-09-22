package com.mayankbhati.movietickets.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mayankbhati.movietickets.catalog.application.CatalogService;
import com.mayankbhati.movietickets.catalog.application.CatalogService.RefundRuleInput;
import com.mayankbhati.movietickets.catalog.application.CatalogService.ScreenCreated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final CatalogService catalog;

    public AdminCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createCity(@Valid @RequestBody CreateCity request) {
        return new IdResponse(catalog.createCity(request.name(), request.timezone()));
    }

    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createTheater(@Valid @RequestBody CreateTheater request) {
        return new IdResponse(catalog.createTheater(request.cityId(), request.name(), request.address()));
    }

    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    ScreenCreated createScreen(@Valid @RequestBody CreateScreen request) {
        return catalog.createScreen(request.theaterId(), request.name(), request.rows(),
                request.seatsPerRow(), request.premiumRows());
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createMovie(@Valid @RequestBody CreateMovie request) {
        return new IdResponse(catalog.createMovie(request.title(), request.durationMinutes(),
                request.certificate(), request.language()));
    }

    @PutMapping("/pricing-tiers/{category}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updatePricing(@PathVariable String category, @Valid @RequestBody UpdatePricing request) {
        catalog.updatePricingTier(category, request.multiplierBps());
    }

    @PostMapping("/refund-policies")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createRefundPolicy(@Valid @RequestBody CreateRefundPolicy request) {
        List<RefundRuleInput> rules = request.rules().stream()
                .map(rule -> new RefundRuleInput(rule.minimumMinutesBefore(), rule.refundPercent()))
                .toList();
        return new IdResponse(catalog.createRefundPolicy(request.name(), rules));
    }

    @PostMapping("/showings")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createShowing(@Valid @RequestBody CreateShowing request) {
        return new IdResponse(catalog.createShowing(request.movieId(), request.screenId(),
                request.refundPolicyId(), request.startsAt(), request.basePriceCents(),
                request.weekendMultiplierBps()));
    }

    @PostMapping("/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    void createDiscount(@Valid @RequestBody CreateDiscount request) {
        catalog.createDiscount(request.code(), request.kind(), request.valueAmount(),
                request.minimumOrderCents(), request.validFrom(), request.validUntil(),
                request.maxRedemptions());
    }

    public record IdResponse(long id) {
    }

    public record CreateCity(@NotBlank @Size(max = 120) String name,
                             @NotBlank @Size(max = 60) String timezone) {
    }

    public record CreateTheater(@Min(1) long cityId, @NotBlank @Size(max = 160) String name,
                                @NotBlank @Size(max = 500) String address) {
    }

    public record CreateScreen(@Min(1) long theaterId, @NotBlank @Size(max = 80) String name,
                               @Min(1) @Max(26) int rows, @Min(1) @Max(100) int seatsPerRow,
                               @NotNull Set<@Min(1) Integer> premiumRows) {
    }

    public record CreateMovie(@NotBlank @Size(max = 200) String title,
                              @Min(1) @Max(600) int durationMinutes,
                              @Size(max = 20) String certificate,
                              @NotBlank @Size(max = 60) String language) {
    }

    public record UpdatePricing(@Min(1000) @Max(100000) int multiplierBps) {
    }

    public record CreateRefundPolicy(@NotBlank @Size(max = 120) String name,
                                     @NotEmpty List<@Valid RefundRule> rules) {
    }

    public record RefundRule(@Min(0) long minimumMinutesBefore,
                             @Min(0) @Max(100) int refundPercent) {
    }

    public record CreateShowing(@Min(1) long movieId, @Min(1) long screenId,
                                @Min(1) long refundPolicyId, @NotNull @Future Instant startsAt,
                                @Min(1) long basePriceCents,
                                @Min(1000) @Max(100000) int weekendMultiplierBps) {
    }

    public record CreateDiscount(@NotBlank @Size(max = 40) String code,
                                 @NotBlank String kind, @Min(1) long valueAmount,
                                 @Min(0) long minimumOrderCents, @NotNull Instant validFrom,
                                 @NotNull Instant validUntil, @Min(1) Integer maxRedemptions) {
    }
}

