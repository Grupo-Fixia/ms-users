package com.fixia.users.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    public static final String BEARER = "Bearer";

    /** Evita que los tokens aparezcan en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
