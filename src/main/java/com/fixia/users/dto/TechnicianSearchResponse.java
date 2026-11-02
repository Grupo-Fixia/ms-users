package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;

import java.util.List;
import java.util.UUID;

/**
 * Una página de técnicos que ofrecen la categoría y atienden la ciudad consultadas, en orden estable por
 * {@code userId} y sin ranking. {@code nextCursor} es nulo solo cuando no quedan técnicos por revisar: una página puede
 * venir corta, o vacía, con cursor no nulo si la consulta agotó su presupuesto de revisión (igual que las
 * oportunidades de ms-services, ADR-044).
 *
 * <p>Solo para consumo interno. {@code circle} (centro y radio de la zona) puede revelar el domicilio del técnico:
 * por eso esta respuesta no sale por el gateway y quien la consume no debe devolverla a un cliente final.
 */
public record TechnicianSearchResponse(List<Item> items, String nextCursor) {

    /** {@code circle} es nulo si el técnico atiende toda la ciudad sin precisar un círculo. */
    public record Item(
            UUID userId,
            String displayName,
            List<ServiceCategory> categories,
            String city,
            Circle circle
    ) {
    }

    public record Circle(double latitude, double longitude, int radiusKm) {
    }
}
