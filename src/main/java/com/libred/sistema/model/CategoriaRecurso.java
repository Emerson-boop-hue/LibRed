package com.libred.sistema.model;

/**
 * Categorías de artículo (se guardan en la columna recurso.tipo_recurso).
 * Son las mismas que usa el inventario del administrador. Aquí se les da un nombre
 * legible y un icono para mostrarlas al lector.
 */
public enum CategoriaRecurso {

    LIBROS("LIBROS", "Libros", "fa-book"),
    NOVELAS_LIGERAS("NOVELAS LIGERAS", "Novelas ligeras", "fa-book-open"),
    CUENTOS("CUENTOS", "Cuentos", "fa-feather-pointed"),
    COMICS("COMICS", "Cómics", "fa-mask"),
    MANGAS("MANGAS", "Mangas", "fa-scroll"),
    REVISTAS("REVISTAS", "Revistas", "fa-newspaper"),
    OTROS("OTROS", "Otros", "fa-box-archive");

    private final String valor;     // lo que está guardado en la BD
    private final String etiqueta;  // lo que ve el lector
    private final String icono;     // clase de Font Awesome

    CategoriaRecurso(String valor, String etiqueta, String icono) {
        this.valor = valor;
        this.etiqueta = etiqueta;
        this.icono = icono;
    }

    public String getValor() { return valor; }
    public String getEtiqueta() { return etiqueta; }
    public String getIcono() { return icono; }

    /** Devuelve la categoría que corresponde al valor guardado en la BD (OTROS si no se reconoce). */
    public static CategoriaRecurso desdeValor(String valor) {
        for (CategoriaRecurso c : values()) {
            if (c.valor.equalsIgnoreCase(valor)) {
                return c;
            }
        }
        return OTROS;
    }

    /** true si el valor es una categoría conocida (sirve para validar el filtro). */
    public static boolean esValida(String valor) {
        for (CategoriaRecurso c : values()) {
            if (c.valor.equalsIgnoreCase(valor)) {
                return true;
            }
        }
        return false;
    }
}