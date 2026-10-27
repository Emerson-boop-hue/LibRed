# LibRed

LibRed es un sistema web de biblioteca orientado a lectores (B2C). El registro de cambios disponible describe autenticación de lectores, registro de cuentas, navegación protegida y consulta de un catálogo de recursos.

> **Nota de verificación:** solo se recibió el contenido anterior de este README, no el árbol ni los archivos fuente del repositorio. Por ello, no fue posible revisar ni contrastar esta documentación con el código actual. Se conservan los datos consignados y se marcan como pendientes las versiones, configuraciones y detalles de implementación que no pueden comprobarse. No debe interpretarse como una auditoría del proyecto.

## Tecnologías y requisitos

El historial menciona clases y conceptos del ecosistema Java/Spring (`@ManyToOne`, controladores y `HttpSession`), pero no permite confirmar las dependencias, versiones ni la herramienta de compilación. Aunque el proyecto se plantea con Java, Spring Boot, Thymeleaf, Bootstrap y MySQL, su presencia y versiones deben verificarse en los archivos del proyecto.

| Requisito | Estado |
| --- | --- |
| Versión de Java | Pendiente de verificar en la configuración del proyecto. |
| Spring Boot, Thymeleaf y Bootstrap | Dependencias y versiones pendientes de verificar. |
| MySQL | Motor mencionado en los requisitos del proyecto; configuración concreta pendiente de verificar. |
| Maven o Gradle | Herramienta y versión pendientes de verificar. |

## Configuración y ejecución

No se proporcionaron archivos de configuración, de construcción ni scripts SQL. Por tanto, no es posible confirmar el nombre de la base de datos, la URL JDBC, los nombres de propiedades o variables de entorno, ni los comandos exactos para compilar y ejecutar.

1. Revisar `pom.xml` o `build.gradle` para identificar la herramienta y versiones requeridas.
2. Revisar los archivos de configuración de Spring y los scripts SQL para determinar el motor, crear o seleccionar la base de datos y configurar la conexión.
3. Configurar localmente usuario y contraseña mediante el mecanismo que use el proyecto. No guardar credenciales ni secretos en el README o en el control de versiones.
4. Abrir la carpeta raíz en Visual Studio Code y usar las extensiones Java/Spring adecuadas a las dependencias verificadas. Los pasos específicos de ejecución están pendientes de confirmar.

Los comandos de instalación, compilación y arranque quedan pendientes de verificar porque no se conoce si el repositorio utiliza Maven, Gradle ni si incluye sus wrappers. El historial recomienda preparar la base de datos e insertar seeders para pruebas; confirmar primero qué scripts existen y utilizar una base de desarrollo. No borrar datos sin respaldo.

## Estructura y arquitectura

No se proporcionó el árbol de carpetas ni los paquetes. Las siguientes responsabilidades se deducen únicamente de las clases citadas en el historial:

| Capa o componente | Información consignada |
| --- | --- |
| Configuración | `WebConfig`, descrita como configuración de protección de rutas. |
| Controladores | `AuthController` para autenticación/registro y `LectorController` para rutas de lectores. |
| Servicios | `AuthService` para inicio de sesión y `CatalogoService` para datos del catálogo. |
| Persistencia | `LibreriaRepository`, utilizado por `AuthController`; otros repositorios no están identificados. |
| Vistas y recursos estáticos | Rutas, motor de plantillas y estructura pendientes de verificar. |

El historial señala que el inicio de sesión retorna `Optional<Lector>`, guarda `lectorId` en `HttpSession` y que las rutas del lector validan la sesión. Es necesario cotejar estos detalles con el código vigente.

## Funcionalidades y rutas consignadas

| Ruta o funcionalidad | Controlador / estado según el historial |
| --- | --- |
| `/catalogo` | `LectorController`; se indica que incorpora al modelo una colección obtenida con `CatalogoService`. |
| `GET /registro` y `POST /registro` | `AuthController`; se indican como implementadas para el registro de lectores. |
| `POST /logout` | `AuthController`; se indica que invalida la sesión. |
| `/lector/panel` | Delegada a `LectorController` para evitar un mapeo duplicado. |
| Resto de navegación del lector | El historial dice que está asociada a `lectorId` en sesión, pero no enumera las rutas. |

Las rutas y su comportamiento deben verificarse en los controladores actuales. No se cuenta con información comprobable sobre las demás pantallas o funcionalidades.

### Registro de lectores

El historial indica que `AuthController` implementa `GET /registro` y `POST /registro`, pero no se recibió su código, la vista ni el modelo para comprobar el flujo actual. Por tanto, quedan pendientes de verificar:

- Si el enlace «Regístrate aquí» de la pantalla de inicio de sesión lleva a `/registro` (el historial indicaba que podía apuntar a `#`).
- La ubicación de `registro.html`, los campos reales del formulario, los parámetros recibidos por el controlador y su correspondencia con los atributos de `Lector`.
- Si las librerías se cargan desde la base de datos, cómo se asocia la librería seleccionada con el lector y si se invoca `AuthService.registrarLector()`.
- El archivo CSS y los estilos utilizados, la redirección después del envío y la existencia y contenido de mensajes de confirmación o error.
- Si se implementan validaciones de contraseña, cifrado de contraseñas o comprobaciones de datos duplicados.

Estos detalles deben cotejarse con los archivos del proyecto antes de documentarlos como comportamiento implementado. No se infieren atributos del modelo ni se documenta el correo electrónico como atributo de `Lector`.

## Modelo de datos

El historial menciona `Lector` y las clases `LMulta`, `LRecurso`, `LPrestamo`, `LApartado` y `LColaEspera`, además de relaciones `@ManyToOne`. También califica el modelo como incompleto. Sin entidades ni scripts SQL no se pueden confirmar sus campos, nombres de tablas, restricciones ni relaciones reales.

### Ajustes

Ajustes debe permitir editar únicamente atributos que existan en la entidad `Lector` y estén habilitados por la aplicación. El historial previo indicaba como pendientes `telefono`, `correo` y la preferencia de notificaciones, por lo que no se documentan como atributos implementados. Los atributos reales de `Lector` y los campos editables quedan pendientes de cotejar con la entidad, el controlador y la vista actuales.

### Ayuda y soporte

El alcance solicitado para esta sección es consultar preguntas frecuentes y registrar solicitudes de soporte, pero el contenido anterior no identifica las vistas, controladores, entidad ni tabla que lo implementan. No se pudieron verificar los atributos reales de `SolicitudSoporte` ni el SQL de `solicitud_soporte`.

La documentación de `SolicitudSoporte` y `solicitud_soporte` **no debe incluir un atributo o columna `correo`**. Antes de completar los demás campos, cotejar la clase y el esquema SQL reales; no inferirlos a partir de `Lector`.

## Estado del desarrollo

### Cambios registrados previamente

- Se creó `WebConfig` para proteger rutas y se trasladaron rutas de lectores a `LectorController`.
- Se conectó `CatalogoService` con la ruta `/catalogo`.
- Se consignaron el registro `GET`/`POST /registro`, el cierre `POST /logout` y el uso de `lectorId` en la sesión.
- Se indicó que `/lector/panel` quedó en `LectorController` para evitar un mapeo duplicado.

Lo anterior es un registro histórico, no una confirmación del estado actual del repositorio.

### Pendientes registrados o por verificar

- Verificar los atributos reales de `Lector`, los campos editables en Ajustes y los valores válidos de `estadoCuenta` (el historial menciona `ACTIVO` y también `HABILITADO`/`SANCIONADO`).
- Revisar la conexión del enlace «Regístrate aquí» con la pantalla de registro; el historial decía que apuntaba a `#`.
- Verificar el flujo de registro en `AuthController`, la plantilla y sus estilos, los campos y parámetros reales, la carga y asociación de librerías, el uso de `AuthService.registrarLector()`, la redirección y los mensajes. No se recibió código fuente para confirmar estos comportamientos ni la validación, cifrado o detección de duplicados.
- Verificar el filtro por librería afiliada; el historial indicaba que el selector estaba hardcodeado y no se procesaba en el controlador.
- Determinar el estado real de las entidades y tablas de recursos, préstamos, cola de espera, apartados y multas.
- Inspeccionar la implementación de preguntas frecuentes y solicitudes de soporte, así como `SolicitudSoporte` y el SQL de `solicitud_soporte`, sin añadir `correo`.
- Confirmar dependencias y versiones, paquetes, vistas, configuración de MySQL, scripts, comandos de ejecución y pruebas.

## Solución de problemas

- **Fallo de conexión a MySQL:** comprobar que el servidor esté disponible, que la base exista y que la URL y credenciales locales correspondan a la configuración del proyecto. Los nombres exactos de las propiedades están pendientes de verificar.
- **Error al compilar o iniciar:** confirmar primero la versión de Java y si el proyecto utiliza Maven o Gradle; no se pueden proporcionar comandos exactos sin inspeccionar sus archivos.
- **Ruta o vista no encontrada:** revisar los mapeos reales de los controladores y la ubicación/nombre de las plantillas.
- **Datos de prueba ausentes:** localizar los seeders o scripts disponibles y ejecutarlos solo sobre una base de desarrollo, después de respaldar cualquier dato que se necesite conservar.
