package com.fixia.users.dto;

/**
 * {@code associated} es verdadero solo si la empresa y el técnico existen, ambas cuentas están activas y el técnico
 * está asociado a esa empresa. Cualquier otro caso (cuenta inexistente, deshabilitada, de otro rol o técnico de otra
 * empresa) responde falso con la misma forma, sin revelar cuál fue el motivo.
 */
public record TechnicianAssociationResponse(boolean associated) {
}
