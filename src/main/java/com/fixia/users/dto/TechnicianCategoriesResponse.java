package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;

import java.time.Instant;
import java.util.Set;

public record TechnicianCategoriesResponse(
        Set<ServiceCategory> categories,
        Instant updatedAt
) {

    public static TechnicianCategoriesResponse from(Technician technician) {
        return new TechnicianCategoriesResponse(technician.getCategories(), technician.getUpdatedAt());
    }
}
