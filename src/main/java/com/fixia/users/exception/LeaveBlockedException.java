package com.fixia.users.exception;

import java.util.List;
import java.util.UUID;

/**
 * El técnico no puede desvincularse: tiene trabajo pendiente o dinero retenido con la empresa (GC-524, regla 3 del
 * contrato de empresas). Se traduce a 409 con los motivos y las solicitudes que lo impiden; no cambia nada.
 */
public class LeaveBlockedException extends RuntimeException {

    /** Postulaciones abiertas o servicios seleccionados sin cerrar con la empresa. */
    public static final String PENDING_WORK = "PENDING_WORK";

    /** Pagos de la empresa retenidos o por retener en servicios que ejecutó. */
    public static final String HELD_BALANCE = "HELD_BALANCE";

    private final List<String> reasons;
    private final List<UUID> serviceRequestIds;

    public LeaveBlockedException(List<String> reasons, List<UUID> serviceRequestIds) {
        super("No puedes desvincularte mientras tengas trabajos pendientes o saldos retenidos con la empresa");
        this.reasons = List.copyOf(reasons);
        this.serviceRequestIds = List.copyOf(serviceRequestIds);
    }

    public List<String> getReasons() {
        return reasons;
    }

    public List<UUID> getServiceRequestIds() {
        return serviceRequestIds;
    }
}
