package com.joshuamc1.sicam.models.dto;

public class AuditDTO {
    private Integer id;
    private Integer usuarioId;
    private String usuarioName;
    private String username;
    private String accion;
    private String tablaAfectada;
    private Integer registroAfectadoId;
    private String detalles;
    private String fecha;

    public AuditDTO(Integer id, Integer usuarioId, String usuarioName, String accion, String tablaAfectada, Integer registroAfectadoId, String detalles, String fecha) {
        this(id, usuarioId, usuarioName, accion, tablaAfectada, registroAfectadoId, detalles, fecha, null);
    }

    public AuditDTO(Integer id, Integer usuarioId, String usuarioName, String accion, String tablaAfectada,
                    Integer registroAfectadoId, String detalles, String fecha, String username) {
        this.username = username;
        this.id = id;
        this.usuarioId = usuarioId;
        this.usuarioName = usuarioName;
        this.accion = accion;
        this.tablaAfectada = tablaAfectada;
        this.registroAfectadoId = registroAfectadoId;
        this.detalles = detalles;
        this.fecha = fecha;
    }

    public Integer getId() { return id; }
    public Integer getUsuarioId() { return usuarioId; }
    public String getUsuarioName() { return usuarioName; }
    public String getUsername() { return username; }
    public String getAccion() { return accion; }
    public String getTablaAfectada() { return tablaAfectada; }
    public Integer getRegistroAfectadoId() { return registroAfectadoId; }
    public String getDetalles() { return detalles; }
    public String getFecha() { return fecha; }
}

