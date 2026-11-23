package com.libred.sistema.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

/** Guarda y borra las imágenes de los artículos. */
@Service
public class ImagenService {

    private static final int ANCHO_FINAL = 480;
    private static final int ALTO_FINAL = 600;
    private static final long MAX_PIXELES = 40_000_000L; // protege la memoria ante imágenes gigantes
    private static final Pattern NOMBRE_VALIDO =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");

    private final Path carpetaArticulos;

    // libred.uploads.dir (application.properties): si se deja vacío se usa <carpeta del usuario>/libred-uploads
    public ImagenService(@Value("${libred.uploads.dir:}") String carpetaConfigurada) throws IOException {
        Path base = (carpetaConfigurada == null || carpetaConfigurada.isBlank())
                ? Paths.get(System.getProperty("user.home"), "libred-uploads")
                : Paths.get(carpetaConfigurada.trim());
        this.carpetaArticulos = base.resolve("articulos").toAbsolutePath().normalize();
        Files.createDirectories(this.carpetaArticulos);
    }

    public Path getCarpetaArticulos() {
        return carpetaArticulos;
    }

    /**
     * Valida la imagen recibida, la guarda y devuelve el nombre del archivo (para guardarlo en la base de datos).
     */
    public String guardarArticulo(MultipartFile archivo) throws Exception {
        if (archivo == null || archivo.isEmpty()) {
            throw new Exception("No se recibió ninguna imagen.");
        }
        BufferedImage original = leerImagen(archivo);
        BufferedImage resultado = ajustar(original);

        String nombre = UUID.randomUUID() + ".jpg";
        Path destino = carpetaArticulos.resolve(nombre).normalize();
        if (!destino.startsWith(carpetaArticulos)) {
            throw new Exception("Nombre de archivo no válido.");
        }
        escribirJpeg(resultado, destino);
        return nombre;
    }

    /**
     * Guarda una imagen que llega como bytes (por ejemplo una portada descargada). Misma validación y tamaño final.
     */
    public String guardarBytes(byte[] datos) throws Exception {
        if (datos == null || datos.length == 0) {
            throw new Exception("No se recibió ninguna imagen.");
        }
        BufferedImage original;
        try (InputStream entrada = new ByteArrayInputStream(datos)) {
            original = ImageIO.read(entrada);
        }
        if (original == null || (long) original.getWidth() * original.getHeight() > MAX_PIXELES) {
            throw new Exception("No se pudo leer la imagen.");
        }
        String nombre = UUID.randomUUID() + ".jpg";
        Path destino = carpetaArticulos.resolve(nombre).normalize();
        if (!destino.startsWith(carpetaArticulos)) {
            throw new Exception("Nombre de archivo no válido.");
        }
        escribirJpeg(ajustar(original), destino);
        return nombre;
    }

    /** Borra el archivo de una imagen (si existe). No falla si el nombre no es válido o el archivo ya no está. */
    public void eliminar(String nombre) {
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(carpetaArticulos.resolve(nombre).normalize());
        } catch (IOException ignorada) {
            // no es crítico: el archivo queda huérfano pero la aplicación sigue funcionando
        }
    }

    // Comprueba que el archivo sea una imagen de verdad (no solo por la extensión) y la lee
    private BufferedImage leerImagen(MultipartFile archivo) throws Exception {
        try (InputStream entrada = archivo.getInputStream();
             ImageInputStream flujo = ImageIO.createImageInputStream(entrada)) {
            if (flujo == null) {
                throw new Exception("No se pudo leer la imagen.");
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                throw new Exception("El archivo no es una imagen válida. Usa una imagen JPG o PNG.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo, true, true);
                long ancho = lector.getWidth(0);
                long alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || ancho * alto > MAX_PIXELES) {
                    throw new Exception("La imagen es demasiado grande.");
                }
                BufferedImage imagen = lector.read(0);
                if (imagen == null) {
                    throw new Exception("No se pudo leer la imagen.");
                }
                return imagen;
            } finally {
                lector.dispose();
            }
        }
    }

    // Deja la imagen en 480 x 600.
    private BufferedImage ajustar(BufferedImage origen) {
        BufferedImage destino = new BufferedImage(ANCHO_FINAL, ALTO_FINAL, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(new Color(51, 65, 85));
            g.fillRect(0, 0, ANCHO_FINAL, ALTO_FINAL);

            double escala = Math.min((double) ANCHO_FINAL / origen.getWidth(), (double) ALTO_FINAL / origen.getHeight());
            int ancho = (int) Math.round(origen.getWidth() * escala);
            int alto = (int) Math.round(origen.getHeight() * escala);
            g.drawImage(origen, (ANCHO_FINAL - ancho) / 2, (ALTO_FINAL - alto) / 2, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private void escribirJpeg(BufferedImage imagen, Path destino) throws IOException {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpg");
        if (!escritores.hasNext()) {
            throw new IOException("No hay un codificador JPEG disponible.");
        }
        ImageWriter escritor = escritores.next();
        try (OutputStream salida = Files.newOutputStream(destino);
             ImageOutputStream flujo = ImageIO.createImageOutputStream(salida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.9f);
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
    }
}