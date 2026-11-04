package com.fixia.users.controller;

import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.dto.ServiceCategoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Catálogo habilitado de especialidades y servicios (RF-010). Lo puede consultar cualquier usuario autenticado. */
@RestController
@RequestMapping("/api/users/service-categories")
public class ServiceCategoryController {

    @GetMapping
    public List<ServiceCategoryResponse> list() {
        return Arrays.stream(ServiceCategory.values()).map(ServiceCategoryResponse::from).toList();
    }
}
