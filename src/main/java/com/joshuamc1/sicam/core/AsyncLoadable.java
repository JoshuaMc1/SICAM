package com.joshuamc1.sicam.core;

/**
 * Interface para controladores que cargan datos de forma asíncrona
 * Permite que quien carga la vista (ej. MainController) sepa si el controlador
 * aún está cargando y registrarse para una notificación cuando termine.
 */
public interface AsyncLoadable {
    boolean isLoading();
    void setOnLoaded(Runnable callback);
}

