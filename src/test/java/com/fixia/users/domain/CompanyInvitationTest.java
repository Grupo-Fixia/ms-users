package com.fixia.users.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyInvitationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(7 * 24 * 3600);

    private static CompanyInvitation invitation() {
        return new CompanyInvitation(UUID.randomUUID(), UUID.randomUUID(), NOW, EXPIRES);
    }

    @Test
    void naceComoPendienteYAbierta() {
        CompanyInvitation invitation = invitation();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.isOpen(NOW)).isTrue();
        assertThat(invitation.isExpired(NOW)).isFalse();
        assertThat(invitation.displayStatus(NOW)).isEqualTo(InvitationDisplayStatus.PENDING);
        assertThat(invitation.getRespondedAt()).isNull();
        assertThat(invitation.getCancelReason()).isNull();
    }

    @Test
    void venceExactamenteEnSuInstanteDeVencimiento() {
        CompanyInvitation invitation = invitation();
        assertThat(invitation.isOpen(EXPIRES.minusMillis(1))).isTrue();
        assertThat(invitation.isOpen(EXPIRES)).isFalse();
        assertThat(invitation.isExpired(EXPIRES)).isTrue();
        assertThat(invitation.displayStatus(EXPIRES)).isEqualTo(InvitationDisplayStatus.EXPIRED);
        // Sigue guardada como pendiente: la caducidad se calcula, no se guarda.
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void laEmpresaPuedeCancelarUnaAbierta() {
        CompanyInvitation invitation = invitation();
        Instant later = NOW.plusSeconds(60);
        invitation.cancelByCompany(later);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(invitation.getCancelReason()).isEqualTo(InvitationCancelReason.BY_COMPANY);
        assertThat(invitation.getRespondedAt()).isEqualTo(later);
        assertThat(invitation.displayStatus(later)).isEqualTo(InvitationDisplayStatus.CANCELLED);
        assertThat(invitation.isOpen(later)).isFalse();
    }

    @Test
    void unaVencidaCerradaAlReinvitarSigueViendoseVencida() {
        CompanyInvitation invitation = invitation();
        invitation.closeAsExpired(EXPIRES.plusSeconds(1));
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(invitation.getCancelReason()).isEqualTo(InvitationCancelReason.EXPIRED);
        assertThat(invitation.displayStatus(EXPIRES.plusSeconds(1))).isEqualTo(InvitationDisplayStatus.EXPIRED);
    }

    @Test
    void unaInvitacionYaCerradaNoCambiaDeEstado() {
        CompanyInvitation invitation = invitation();
        invitation.cancelByCompany(NOW.plusSeconds(1));
        assertThatThrownBy(() -> invitation.cancelByCompany(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invitation.closeAsExpired(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeVencerDespuesDeCrearse() {
        assertThatThrownBy(() -> new CompanyInvitation(UUID.randomUUID(), UUID.randomUUID(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
