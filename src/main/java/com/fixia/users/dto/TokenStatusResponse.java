package com.fixia.users.dto;

/**
 * Estado de revocación de un access token. Cuerpo mínimo a propósito: no incluye el usuario, los roles ni el
 * estado de la cuenta.
 */
public record TokenStatusResponse(boolean revoked) {
}
