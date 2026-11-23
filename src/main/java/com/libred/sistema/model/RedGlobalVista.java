package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/** Datos que muestra la pantalla Red Global del lector (solo lectura). */
public class RedGlobalVista {

    /** Una categoría que la librería ofrece (con su icono). */
    public static class CategoriaChip {
        private final String valor;
        private final String etiqueta;
        private final String icono;

        public CategoriaChip(String valor, String etiqueta, String icono) {
            this.valor = valor;
            this.etiqueta = etiqueta;
            this.icono = icono;
        }

        public String getValor() { return valor; }
        public String getEtiqueta() { return etiqueta; }
        public String getIcono() { return icono; }
    }

    /** Una barra de la distribución de votos (cuántos votos dieron N estrellas). */
    public static class BarraVoto {
        private final int estrellas;
        private final long votos;
        private final int porcentaje;

        public BarraVoto(int estrellas, long votos, int porcentaje) {
            this.estrellas = estrellas;
            this.votos = votos;
            this.porcentaje = porcentaje;
        }

        public int getEstrellas() { return estrellas; }
        public long getVotos() { return votos; }
        public int getPorcentaje() { return porcentaje; }
    }

    /** Tarjeta de una librería de la red. */
    public static class TarjetaLibreria {
        private final Integer id;
        private final String nombre;
        private final String iniciales;
        private final int tono;              // 0..5: color del avatar
        private final String direccion;
        private final String telefono;
        private final String correo;
        private final String horario;
        private final String descripcion;
        private final String miembroDesde;   // "marzo de 2026"
        private final long articulos;        // unidades distintas (títulos) en catálogo
        private final int maxDias;
        private final Integer maxActivos;    // null = sin límite
        private final BigDecimal tarifaDesde; // la tarifa más baja entre sus categorías activas
        private final List<CategoriaChip> categorias;
        private final boolean sedeActual;
        private final boolean miembro;
        private final long[] votosPorEstrella; // posición 0 = votos de 1 estrella ... 4 = votos de 5 estrellas

        public TarjetaLibreria(Integer id, String nombre, String iniciales, int tono,
                               String direccion, String telefono, String correo, String horario,
                               String descripcion, String miembroDesde, long articulos,
                               int maxDias, Integer maxActivos, BigDecimal tarifaDesde,
                               List<CategoriaChip> categorias, boolean sedeActual, boolean miembro,
                               long[] votosPorEstrella) {
            this.id = id;
            this.nombre = nombre;
            this.iniciales = iniciales;
            this.tono = tono;
            this.direccion = direccion;
            this.telefono = telefono;
            this.correo = correo;
            this.horario = horario;
            this.descripcion = descripcion;
            this.miembroDesde = miembroDesde;
            this.articulos = articulos;
            this.maxDias = maxDias;
            this.maxActivos = maxActivos;
            this.tarifaDesde = tarifaDesde;
            this.categorias = categorias;
            this.sedeActual = sedeActual;
            this.miembro = miembro;
            this.votosPorEstrella = votosPorEstrella;
        }

        public Integer getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public int getTono() { return tono; }
        public String getDireccion() { return direccion; }
        public String getTelefono() { return telefono; }
        public String getCorreo() { return correo; }
        public String getHorario() { return horario; }
        public String getDescripcion() { return descripcion; }
        public String getMiembroDesde() { return miembroDesde; }
        public long getArticulos() { return articulos; }
        public int getMaxDias() { return maxDias; }
        public Integer getMaxActivos() { return maxActivos; }
        public boolean isLimitado() { return maxActivos != null; }
        public BigDecimal getTarifaDesde() { return tarifaDesde; }
        public List<CategoriaChip> getCategorias() { return categorias; }
        /** Claves de sus categorías separadas por | (para filtrar en pantalla): |LIBROS|MANGAS| */
        public String getClavesCategorias() {
            StringBuilder sb = new StringBuilder("|");
            for (CategoriaChip c : categorias) {
                sb.append(c.getValor()).append('|');
            }
            return sb.toString();
        }
        // ----- Calificación (promedio de las calificaciones de sus préstamos) -----
        public long getVotos() {
            long total = 0;
            for (long v : votosPorEstrella) {
                total += v;
            }
            return total;
        }
        public boolean isConVotos() { return getVotos() > 0; }
        public double getPromedio() {
            long total = getVotos();
            if (total == 0) {
                return 0;
            }
            long suma = 0;
            for (int i = 0; i < votosPorEstrella.length; i++) {
                suma += votosPorEstrella[i] * (i + 1);
            }
            return (double) suma / total;
        }
        /** "4.6" (siempre con punto decimal) */
        public String getPromedioTexto() { return String.format(java.util.Locale.US, "%.1f", getPromedio()); }
        /** Porcentaje de estrellas llenas (0 a 100) para pintar las estrellas. */
        public int getPorcentajeEstrellas() { return (int) Math.round(getPromedio() / 5.0 * 100); }
        /** Barras de la distribución, de 5 a 1 estrellas. */
        public List<BarraVoto> getBarras() {
            long total = getVotos();
            List<BarraVoto> barras = new java.util.ArrayList<>();
            for (int e = 5; e >= 1; e--) {
                long v = votosPorEstrella[e - 1];
                barras.add(new BarraVoto(e, v, total == 0 ? 0 : (int) Math.round(v * 100.0 / total)));
            }
            return barras;
        }
        public boolean isSedeActual() { return sedeActual; }
        public boolean isMiembro() { return miembro; }
    }

    private final List<TarjetaLibreria> librerias;
    private final List<CategoriaChip> categorias; // todas las categorías que ofrece alguna librería
    private final long totalArticulos;

    public RedGlobalVista(List<TarjetaLibreria> librerias,
                          List<CategoriaChip> categorias, long totalArticulos) {
        this.librerias = librerias;
        this.categorias = categorias;
        this.totalArticulos = totalArticulos;
    }

    public List<TarjetaLibreria> getLibrerias() { return librerias; }
    public List<CategoriaChip> getCategorias() { return categorias; }
    public long getTotalArticulos() { return totalArticulos; }
    public int getTotalLibrerias() { return librerias.size(); }
}