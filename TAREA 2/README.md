# Tarea 2 — Mensajería distribuida SIPAP con Apache Camel + Artemis ActiveMQ

> **Estado de este entregable:** implementación de referencia generada como
> apoyo para completar la tarea. **No se pudo compilar ni ejecutar en el
> entorno donde se escribió el código** porque ese entorno no tiene acceso
> de red a Maven Central (solo a npm/pip/GitHub). Ver la sección
> [«Qué falta / qué validar»](#qué-falta--qué-validar-antes-de-entregar) al
> final: es la parte más importante de este README.

## 1. Arquitectura y flujo

```
Cliente ──POST /api/transferencias──▶ API REST (Camel REST DSL)
                                            │
                                 parsear QR (heredado T1)
                                 validar monto (<= 10.000.000)
                                            │
                              ┌── rechazo ──┴── aceptado ──┐
                              ▼                             ▼
                     respuesta 422 RECHAZADA        cola Artemis
                                                 sipap.transferencias.entrada
                                                            │
                                              enrutamiento (Content-based)
                                                 + Multicast (auditoría)
                                            ┌───────────────┼───────────────┐
                                            ▼               ▼               ▼
                                 sipap.banco.0001   sipap.banco.0002  sipap.banco.0003
                                            │               │               │
                                   consumidor banco  consumidor banco consumidor banco
                                   (fecha + idempotencia + Request-Reply al mock)
                                            │
                                  POST http://mock/banco/{cod}/transferencias
                                            │
                                 resultado final (PROCESADA / ERROR_BANCO)
                                       + Message Store
```

## 2. Instalación y ejecución

### 2.1 Requisitos
- Java 17+
- Maven 3.9+
- Docker y docker-compose (para Artemis y el banco mock)

### 2.2 Levantar dependencias externas
```bash
docker-compose up -d
```
Esto levanta:
- **Artemis** en `tcp://localhost:61616` (consola web: `http://localhost:8161`, usuario `artemis` / password `artemis`).
- **WireMock** (banco simulado) en `http://localhost:9561`, con los stubs de `wiremock/mappings/`.

### 2.3 Compilar y ejecutar la aplicación
```bash
mvn clean package
mvn spring-boot:run
```
La API queda disponible en `http://localhost:8080`.

### 2.4 Variables de entorno / configuración (`application.yml`)
| Propiedad                     | Descripción                                   | Default                  |
|--------------------------------|------------------------------------------------|---------------------------|
| `sipap.artemis.broker-url`     | URL del broker Artemis (protocolo core/JMS)    | `tcp://localhost:61616`  |
| `sipap.artemis.user`           | Usuario Artemis                                 | `artemis`                |
| `sipap.artemis.password`       | Password Artemis                                | `artemis`                |
| `sipap.bancos.codigos`         | Códigos de entidad para los que se crea consumidor | `0001,0002,0003`     |
| `sipap.mock.baseUrl`           | Base URL del banco simulado (WireMock)         | `http://localhost:9561`  |
| `server.port`                  | Puerto de la API                                | `8080`                    |

## 3. API REST

### `POST /api/transferencias`
- **Headers:** `Content-Type: application/json`
- **Body:**
```json
{
  "id_transaccion": "TX000001",
  "fecha_transaccion": "2026-08-18",
  "qr": "000201010211...6304ABCD",
  "monto": "10"
}
```
- **Respuesta 202 (aceptada, encolada):**
```json
{
  "id_transaccion": "TX000001",
  "estado": "ACEPTADA_PARA_PROCESAMIENTO",
  "mensaje": "Transferencia enviada a la cola"
}
```
- **Respuesta 422 (rechazada por monto):**
```json
{
  "id_transaccion": "TX000001",
  "estado": "RECHAZADA_MONTO",
  "mensaje": "El monto supera máximo permitido"
}
```
- **Respuesta 422 (rechazada por QR/petición inválida):**
```json
{
  "id_transaccion": "TX000001",
  "estado": "RECHAZADA",
  "mensaje": "QR inválido: ..."
}
```
- **Respuesta 500:** error interno no controlado.

La respuesta de la API es **siempre síncrona e inmediata** (aceptación/rechazo
antes de publicar). El resultado del procesamiento del banco (`PROCESADA`,
`ERROR_BANCO`, `RECHAZADA_FECHA`, `DUPLICADA`) ocurre de forma **asíncrona**
en el consumidor y queda registrado en logs y en el `MessageStore` en memoria
(`com.sipap.util.MessageStore`). *(Ver sección "Qué falta" — no se expuso un
endpoint de consulta de resultado final; sería la mejora natural siguiente).*

## 4. Colas Artemis

| Cola                                | Propósito                                              |
|--------------------------------------|---------------------------------------------------------|
| `sipap.transferencias.entrada`      | Cola de entrada, recibe toda transferencia aceptada     |
| `sipap.banco.0001` / `.0002` / `.0003` | Colas punto a punto por entidad financiera destino    |
| `sipap.auditoria`                   | Copia de auditoría (Multicast) de cada transferencia enrutada |

Todas son colas punto a punto (JMS `Queue`), consistente con el requisito de
que cada transferencia sea procesada por un único consumidor destinatario
(no se usan tópicos en el flujo principal).

## 5. Patrones EIP aplicados

1. **Message Channel** — `RoutingToBankQueuesRoute` y `ApiRestRoute` distinguen
   canales *internos* de Camel (`direct:...`, usados solo para orquestar
   dentro del mismo proceso) de canales *externos* (`jms:queue:...`, que
   cruzan el proceso hacia Artemis). Los primeros no sobreviven a un reinicio
   ni son visibles fuera de la JVM; los segundos sí, y son los que dan
   desacoplamiento real entre productor y consumidores.

2. **Idempotent Receiver** — `IdempotencyStore` + `DateAndIdempotencyProcessor`.
   Antes de procesar cualquier transferencia, el consumidor registra
   `id_transaccion` en un mapa en memoria; si ya existía, se descarta con
   estado `DUPLICADA` y **no** se vuelve a invocar al banco mock. Alcance:
   una sola instancia de la aplicación, TTL de 24h, ver limitaciones en el
   javadoc de la clase.

3. **Correlation Identifier** — `id_transaccion` se fija como header Camel
   `idTransaccion` y como `JMSCorrelationID` en `ApiRestRoute`, se propaga
   como propiedad JMS automáticamente hacia los consumidores, se usa en
   todos los `log.info(...)`, se envía como header `X-Id-Transaccion` y
   dentro del cuerpo JSON hacia el banco mock, y aparece en el
   `TransferResponse` final.

4. **Request-Reply** — `BankConsumerRoute` invoca el endpoint REST del banco
   mock con `.toD(...)` en modo síncrono (InOut implícito de `camel-http`),
   espera la respuesta HTTP y `BankResponseProcessor` la traduce al estado
   final (`PROCESADA` / `ERROR_BANCO`).

5. **Multicast** *(bonus, 5º patrón)* — `RoutingToBankQueuesRoute` envía cada
   transferencia simultáneamente a la cola del banco destino y a
   `sipap.auditoria`, con `stopOnException(false)` para que un problema de
   auditoría no bloquee el camino principal.

6. **Message Store** *(bonus)* — `MessageStore` conserva cada transferencia
   procesada junto a su estado final, para trazabilidad/auditoría.

*(El enunciado pide evidenciar al menos 3 nuevos + los 4 marcados como
obligatorios: Message Channel, Idempotent Receiver, Correlation Identifier y
Request-Reply están cubiertos; Multicast y Message Store se agregaron además.)*

## 6. Estados de transferencia

| Estado                        | Dónde se genera                          |
|--------------------------------|--------------------------------------------|
| `ACEPTADA_PARA_PROCESAMIENTO` | API, tras publicar en Artemis              |
| `RECHAZADA`                   | API, petición/QR inválido                  |
| `RECHAZADA_MONTO`             | API, monto > 10.000.000                    |
| `RECHAZADA_FECHA`             | Consumidor, `fecha_transaccion` ≠ hoy      |
| `DUPLICADA`                   | Consumidor, `id_transaccion` ya procesado  |
| `PROCESADA`                   | Consumidor, banco mock respondió 2xx       |
| `ERROR_BANCO`                 | Consumidor, banco mock respondió error / no contactable |

Zona horaria usada para la validación de fecha: **`America/Asuncion`**
(constante `DateRule.ZONA_HORARIA`), aplicada de forma uniforme en la app y
en los tests.

## 7. Bancos simulados (WireMock)

| Código banco | Comportamiento configurado                     |
|--------------|--------------------------------------------------|
| `0001`       | Responde 200 (aprobada)                          |
| `0002`       | Responde 200 (aprobada)                          |
| `0003`       | Responde 400 (rechazada — cuenta inhabilitada)   |

Los stubs están en `wiremock/mappings/*.json` y se montan automáticamente al
levantar `docker-compose up -d`.

## 8. Pruebas incluidas

- `QrTlvParserTest` — parseo de QR estático/dinámico, errores de formato,
  QR sin entidad.
- `AmountRuleTest` — límite exacto de 10.000.000, mayor, menor, nulo.
- `DateRuleTest` — fecha actual, anterior, posterior, nula.
- `IdempotencyStoreTest` — primer mensaje, duplicado, ids distintos.
- `EndToEndFlowTest` — prueba de integración de referencia (Artemis embebido
  + WireMock embebido + petición HTTP real) que cubre: transferencia válida
  aceptada, monto excedido rechazado antes de publicar, y envío de
  `id_transaccion` duplicado. **Marcada como plantilla a validar** (ver
  siguiente sección).

Ejecutar todas las pruebas:
```bash
mvn test
```

## 9. Decisiones de diseño relevantes

- El **parser QR**, el **modelo canónico** (`CanonicalTransfer`) y las
  **reglas de negocio** (`AmountRule`, `DateRule`) no importan nada de
  `org.apache.camel.jms` ni de Artemis: reciben tipos primitivos/de dominio
  y devuelven tipos de dominio. Solo las clases de `routes/` y `config/`
  conocen JMS. Esto permite reemplazar Artemis por Kafka/RabbitMQ más
  adelante tocando solo `JmsConfig` y las rutas.
- Se usa **JMS clásico** (no AMQP 1.0) como implementación de referencia,
  con `pooled-jms` para el pool de conexiones, tal como recomienda Camel
  para el componente `jms`.
- La cola de entrada única (`sipap.transferencias.entrada`) + un enrutador
  posterior a colas por banco fue preferida sobre "publicar directo a la
  cola del banco desde la API" porque separa mejor la responsabilidad de
  "aceptar y encolar" (API) de "decidir el destino y auditar" (enrutador),
  y deja un punto natural para insertar el Multicast de auditoría.

## 10. Qué falta / qué validar antes de entregar

Esto es lo más importante de este README. El código fue escrito
directamente (sin poder compilar) porque el entorno de generación **no
tenía acceso de red a Maven Central** (`repo.maven.apache.org` no estaba en
la lista de dominios permitidos; solo pypi/npm/GitHub). Antes de entregar,
como mínimo:

1. **Compilar (`mvn clean compile`) y corregir errores de compilación.**
   El diseño y las firmas están pensados para Camel 4.4 / Spring Boot 3.2,
   pero nombres exactos de artefactos Maven, versiones compatibles entre
   `camel-spring-boot-bom` y Artemis 2.33.0, o pequeños detalles de API
   (p. ej. `camel-jms` vs configuración de `JmsComponent`, el bean id
   inferido para `.process("parseAndValidateProcessor")`) pueden requerir
   ajustes menores.
2. **Ejecutar `mvn test`** y, en particular, revisar/ajustar
   `EndToEndFlowTest`: quedó marcado en el propio archivo como plantilla de
   referencia — el `Thread.sleep` para esperar el procesamiento asíncrono es
   frágil y debería reemplazarse por un mecanismo determinístico (p. ej.
   exponer un endpoint `GET /api/transferencias/{id}` que consulte
   `MessageStore`, o usar `NotifyBuilder` de camel-test para esperar a que
   la ruta del consumidor termine).
3. **Exponer un endpoint de consulta del resultado final**
   (`GET /api/transferencias/{id_transaccion}`) que lea del `MessageStore`.
   El enunciado no lo exige explícitamente, pero facilita mucho probar y
   demostrar el flujo asíncrono en la entrega (capturas/evidencia).
4. **Probar contra Artemis real vía Docker** (no solo el embebido de test):
   levantar `docker-compose up -d`, correr la app, y golpear la API con
   `curl`/Postman, verificando en la consola de Artemis (`:8161`) que los
   mensajes llegan a las colas esperadas.
5. **Generar las capturas/evidencia de ejecución** que pide la entrega:
   publicación en la cola, consumo, validación de fecha, log de correlación
   por `id_transaccion`, y respuesta del banco mock (2xx y error).
6. **Actualizar el diagrama** (`docs/`) con los endpoints de entrada/salida
   reales una vez validado el flujo (el diagrama de este README es textual;
   conviene convertirlo a una imagen para el entregable final).
7. **Revisar QR reales de la Tarea 1**: el `QrTlvParser` de este repo asume
   un formato TLV tipo EMV genérico (tags 00/01/26-51/53/54/58/59/60/62/63).
   Si el parser real de la Tarea 1 usa tags o una lógica de extracción de
   entidad distinta, hay que alinear `QrTlvParser.parseToCanonical(...)`
   (y los fixtures de test) con esa implementación real, en lugar de la
   reconstrucción hecha acá a partir del enunciado.
8. **Revisar nombres/versión exacta de la imagen Docker de Artemis** en
   `docker-compose.yml` (`apache/activemq-artemis:2.33.0`): confirmar que
   la imagen y las variables de entorno (`ARTEMIS_USER`, etc.) coincidan con
   la versión que efectivamente se descargue.
9. **Prueba de "QR inválido o banco desconocido"**: falta un test explícito
   para el caso "banco desconocido" (entidad presente en el QR pero sin cola
   configurada en `sipap.bancos.codigos`) — hoy ese mensaje se enrutaría a
   una cola `sipap.banco.<entidad>` sin consumidor y quedaría "atascado". Se
   sugiere agregar validación explícita contra la lista de bancos
   soportados en `ParseAndValidateProcessor` antes de publicar.
10. **Seguridad/credenciales**: las credenciales de Artemis están en texto
    plano en `application.yml`/`docker-compose.yml` porque es un entorno de
    desarrollo; documentarlo como tal en la entrega (ya está aclarado acá).

### Resumen rápido de cobertura de requerimientos
| Requerimiento del enunciado                              | Estado |
|------------------------------------------------------------|--------|
| API REST de productores                                    | ✅ Implementado, ⚠️ sin compilar |
| Control de monto antes del broker                           | ✅ Implementado, ⚠️ sin compilar |
| Colas Artemis (entrada + por banco) vía Docker Compose      | ✅ Implementado |
| Consumidores con validación de fecha                        | ✅ Implementado, ⚠️ sin compilar |
| Integración REST con banco simulado (WireMock)               | ✅ Implementado |
| 4 patrones EIP obligatorios + Multicast/Message Store extra  | ✅ Implementado |
| README con documentación                                     | ✅ Este archivo |
| Pruebas mínimas (9 escenarios pedidos)                       | ⚠️ Parcial: cubiertas por unit tests + E2E de referencia; faltan casos "banco desconocido" explícito y verificación formal de "no se encoló / no llegó al mock" |
| Diagrama actualizado                                          | ⚠️ Solo textual en este README, falta imagen |
| Evidencia de ejecución (capturas)                              | ❌ Pendiente — requiere ejecutar localmente |
