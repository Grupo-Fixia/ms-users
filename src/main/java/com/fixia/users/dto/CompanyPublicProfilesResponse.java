package com.fixia.users.dto;

import java.util.List;
import java.util.UUID;

/**
 * Perfil público de las empresas consultadas, en el orden pedido y sin repetidos. Una cuenta que no existe, está
 * deshabilitada, no es de empresa o no tiene empresa cargada no aparece.
 */
public record CompanyPublicProfilesResponse(List<Profile> profiles) {

    /** Solo el nombre comercial: nunca el NIT, el correo ni los datos de la cuenta del administrador. */
    public record Profile(UUID companyAdminUserId, String name) {
    }
}
