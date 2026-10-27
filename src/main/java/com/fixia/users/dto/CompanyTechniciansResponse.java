package com.fixia.users.dto;

import java.util.List;

/**
 * Técnicos asociados a la empresa autenticada (GC-466), con los mismos campos permitidos del perfil público (GC-425):
 * nombre corto, verificación, especialidades, descripción, experiencia y ciudad, nunca correo, teléfono ni documento.
 * El {@code userId} de cada uno es el identificador que ms-services usa para postular y seleccionar al ejecutor. Una
 * empresa sin técnicos recibe la lista vacía; un técnico con cuenta deshabilitada no aparece.
 */
public record CompanyTechniciansResponse(List<PublicProfilesResponse.Profile> technicians) {
}
