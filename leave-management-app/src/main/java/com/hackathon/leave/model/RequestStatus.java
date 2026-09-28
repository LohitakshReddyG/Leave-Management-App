package com.hackathon.leave.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The approval state machine. Every status change in the application goes
 * through canTransitionTo(); anything not allowed here is rejected with 409.
 *
 *   PENDING_MANAGER -> PENDING_HR | REJECTED_BY_MANAGER | CANCELLED | ESCALATED
 *   PENDING_HR      -> APPROVED    | REJECTED_BY_HR      | CANCELLED
 *   ESCALATED       -> PENDING_HR   (pass-through: timeout routes the request to HR)
 *   APPROVED, REJECTED_BY_*, CANCELLED are terminal.
 */
public enum RequestStatus {
    PENDING_MANAGER, PENDING_HR, APPROVED, REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED, ESCALATED;

    public static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = Map.of(
            PENDING_MANAGER, Set.of(PENDING_HR, REJECTED_BY_MANAGER, CANCELLED, ESCALATED),
            PENDING_HR, Set.of(APPROVED, REJECTED_BY_HR, CANCELLED),
            ESCALATED, Set.of(PENDING_HR),
            APPROVED, Set.of(),
            REJECTED_BY_MANAGER, Set.of(),
            REJECTED_BY_HR, Set.of(),
            CANCELLED, Set.of()
    );

    /** Statuses that hold leave days against the employee balance and count for team conflicts. */
    public static final List<RequestStatus> ACTIVE_STATUSES =
            List.of(PENDING_MANAGER, PENDING_HR, ESCALATED, APPROVED);

    public boolean canTransitionTo(RequestStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** True while the request is still awaiting a human decision. */
    public boolean isPending() {
        return this == PENDING_MANAGER || this == PENDING_HR;
    }
}
