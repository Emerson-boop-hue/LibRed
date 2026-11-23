package com.libred.sistema.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Busca portadas de libros en Open Library (gratuito, sin clave). */
@Service
public class PortadaService {

    /** datos = bytes de la imagen; exacta = true si es la portada del libro buscado, false si es de relleno. */
    public record Portada(byte[] datos, boolean exacta) { }

    private static final Pattern COVER_ID = Pattern.compile("\"cover_i\"\\s*:\\s*(\\d+)");
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final int MIN_BYTES = 4 * 1024; // las imágenes "vacías" de Open Library pesan muy poco

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Portadas de relleno por categoría (se piden una sola vez y se reutilizan)
    private final Map<String, List<Long>> rellenoPorCategoria = new ConcurrentHashMap<>();

    public Optional<Portada> buscar(String titulo, String autor, String categoria) {
        if (titulo != null && !titulo.isBlank()) {
            // Primero con título y autor; si no aparece, solo con el título
            for (boolean conAutor : new boolean[]{true, false}) {
                if (conAutor && (autor == null || autor.isBlank())) {
                    continue;
                }
                String url = "https://openlibrary.org/search.json?limit=5&fields=cover_i&title=" + codificar(titulo)
                        + (conAutor ? "&author=" + codificar(autor) : "");
                for (Long id : idsDePortada(descargarTexto(url))) {
                    byte[] datos = descargarPortada(id);
                    if (datos != null) {
                        return Optional.of(new Portada(datos, true));
                    }
                }
            }
        }
        return buscarRelleno(categoria);
    }

    private Optional<Portada> buscarRelleno(String categoria) {
        String clave = categoria == null ? "OTROS" : categoria.toUpperCase();
        List<Long> ids = rellenoPorCategoria.get(clave);
        if (ids == null || ids.isEmpty()) {
            ids = idsDePortada(descargarTexto("https://openlibrary.org/search.json?limit=40&fields=cover_i&q="
                    + codificar(consultaDeCategoria(clave))));
            if (!ids.isEmpty()) {
                rellenoPorCategoria.put(clave, ids);
            }
        }
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<Long> mezcladas = new ArrayList<>(ids);
        Collections.shuffle(mezcladas, ThreadLocalRandom.current());
        for (int i = 0; i < Math.min(4, mezcladas.size()); i++) {
            byte[] datos = descargarPortada(mezcladas.get(i));
            if (datos != null) {
                return Optional.of(new Portada(datos, false));
            }
        }
        return Optional.empty();
    }

    private String consultaDeCategoria(String categoria) {
        return switch (categoria) {
            case "LIBROS" -> "subject:fiction";
            case "NOVELAS LIGERAS" -> "light novel";
            case "CUENTOS" -> "subject:\"short stories\"";
            case "COMICS" -> "subject:comics";
            case "MANGAS" -> "subject:manga";
            case "REVISTAS" -> "subject:magazines";
            default -> "subject:literature";
        };
    }

    private List<Long> idsDePortada(String json) {
        List<Long> ids = new ArrayList<>();
        if (json == null) {
            return ids;
        }
        Matcher m = COVER_ID.matcher(json);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    private byte[] descargarPortada(long id) {
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create("https://covers.openlibrary.org/b/id/" + id + "-L.jpg?default=false"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "LibRed/1.0 (portadas de inventario)")
                    .GET().build();
            HttpResponse<byte[]> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
            byte[] cuerpo = respuesta.body();
            if (respuesta.statusCode() == 200 && cuerpo != null && cuerpo.length >= MIN_BYTES && cuerpo.length <= MAX_BYTES) {
                return cuerpo;
            }
        } catch (IOException e) {
            // sin conexión o tiempo agotado: se prueba con la siguiente
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // dirección inválida u otro error: se ignora
        }
        return null;
    }

    private String descargarTexto(String url) {
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "LibRed/1.0 (portadas de inventario)")
                    .GET().build();
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return respuesta.statusCode() == 200 ? respuesta.body() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String codificar(String texto) {
        return URLEncoder.encode(texto.trim(), StandardCharsets.UTF_8);
    }
}