package com.moviebooking.refund;

import com.moviebooking.refund.RefundDtos.RefundRuleRequest;
import com.moviebooking.refund.RefundDtos.RefundRuleResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/refund-policies")
public class RefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    public RefundPolicyController(RefundPolicyService refundPolicyService) {
        this.refundPolicyService = refundPolicyService;
    }

    /** Lists the refund policy tiers, highest threshold first. */
    @GetMapping
    public List<RefundRuleResponse> list() {
        return refundPolicyService.list();
    }

    /** Creates or updates a refund tier (upsert by minimum-hours-before-show threshold). */
    @PutMapping
    public RefundRuleResponse save(@Valid @RequestBody RefundRuleRequest request) {
        return refundPolicyService.save(request);
    }

    /** Deletes a refund tier by id. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        refundPolicyService.delete(id);
    }
}
