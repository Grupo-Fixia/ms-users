package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;

/** Una entrada del catálogo habilitado: el código que viaja en la API y el nombre para mostrar. */
public record ServiceCategoryResponse(
        ServiceCategory code,
        String name
) {

    public static ServiceCategoryResponse from(ServiceCategory category) {
        return new ServiceCategoryResponse(category, category.getDisplayName());
    }
}
