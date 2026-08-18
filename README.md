# SIPAP QR Integration — Apache Camel

Mediador de integración construido con **Apache Camel** que simula el procesamiento
de transferencias **SIPAP** entre bancos a partir de cadenas QR interoperables
(**EMVCo / SIP Paraguay**). Proyecto académico — Tarea 1 (Apache Camel, patrones
de mensajería y transformación).

> ⚠️ La cadena QR y el CRC (`A1B2`) definidos aquí son **didácticos**. Este
> proyecto no debe usarse para operaciones financieras reales ni representa
> la infraestructura real de SIPAP.

---

## 1. Descripción general

El mediador:

1. Recibe cadenas QR TLV desde dos **productores** simulados (timers).
2. Las **parsea** (Tag-Length-Value) y valida su estructura y reglas de negocio.
3. Las **transforma** a un modelo canónico interno (`Transferencia`).
4. Las **enruta** al banco destino correspondiente (ITAU, ATLAS o FAMILIAR)
   según el código de entidad contenido en el QR.
5. Cada **consumidor** bancario simulado procesa la transferencia y produce
   un resultado común (`PROCESADA` o `RECHAZADA`).
6. Los mensajes inválidos se **rechazan** antes de llegar a cualquier banco,
   registrando el motivo junto al identificador de transacción.

Todo el flujo ocurre **dentro de un mismo `CamelContext`**, usando únicamente
endpoints `direct:` (canales internos, síncronos, sin broker externo).

---

## 2. Estructura del repositorio

```
sipap-camel-integration/
├── pom.xml
├── README.md
├── diagrams/
│   └── flujo-eip.svg              # Diagrama de referencia (notación EIP)
├── examples/
│   ├── qr-validos.txt             # Cadenas QR válidas de ejemplo
│   └── qr-invalidos.txt           # Cadenas QR inválidas de ejemplo (con motivo)
└── src/
    ├── main/java/com/bcp/sipap/
    │   ├── MainApp.java                       # Punto de entrada (camel-main)
    │   ├── model/                             # Clases de dominio
    │   │   ├── MerchantAccountInformation.java
    │   │   ├── Transferencia.java             # Modelo canónico
    │   │   └── ResultadoTransferencia.java
    │   ├── parser/
    │   │   ├── QrTlvParser.java               # Parser TLV genérico
    │   │   ├── QrTlvBuilder.java              # Constructor de cadenas TLV
    │   │   └── QrParseException.java
    │   ├── validation/
    │   │   ├── QrValidator.java               # Reglas de negocio
    │   │   └── ValidationException.java
    │   ├── processor/                         # Processors usados por las rutas
    │   ├── routes/
    │   │   ├── ProductorRoute.java            # 2 productores (timer)
    │   │   ├── MediadorRoute.java             # Parseo/validación/CBR/etc.
    │   │   └── ConsumidorRoute.java           # 3 bancos simulados
    │   └── util/                              # Catálogo de bancos, ids, etc.
    └── test/java/com/bcp/sipap/
        ├── parser/QrTlvParserTest.java
        ├── validation/QrValidatorTest.java
        └── routes/MediadorRouteTest.java
```

---

## 3. Formato de la cadena QR (TLV / EMVCo / SIP Paraguay)

Cada campo: `TAG (2) + LONGITUD (2) + VALOR (n)`.

| Tag | Campo | Ejemplo |
|-----|-------|---------|
| 00 | Payload Format Indicator | `000201` |
| 01 | Point of Initiation Method | `010211` (estático) / `010212` (dinámico) |
| 32 | Merchant Account Information | contiene sub-tags `00`,`01`,`02` |
| 52 | Merchant Category Code | `52045731` |
| 53 | Transaction Currency | `5303600` (PYG) |
| 54 | Transaction Amount | obligatorio si es dinámico |
| 58 | Country Code | `5802PY` |
| 59 | Merchant Name | — |
| 60 | Merchant City | — |
| 63 | CRC (dummy) | `6304A1B2` |

**Sub-tags del bloque 32 (Merchant Account Information):**

| Sub-tag | Campo |
|---------|-------|
| 00 | Globally Unique Identifier → `py.gov.bcp.sip` |
| 01 | Código de entidad (banco), asignado por el BCP |
| 02 | Número de cuenta del beneficiario |

**Bancos simulados en este ejercicio:**

| Código BCP | Banco | ¿Consumidor simulado? |
|---|---|---|
| 0015 | Banco Itaú Paraguay | ✅ |
| 0007 | Banco Atlas | ✅ |
| 0020 | Banco Familiar | ✅ |
| 0017, 0021, 0014, 0024, 0900 | Otros bancos reconocidos por el BCP | ❌ (reconocidos, pero sin consumidor en esta práctica → se rechazan) |
| cualquier otro código | No reconocido | ❌ (rechazado: "Código de entidad no reconocido") |

Ejemplos completos de cadenas válidas e inválidas: ver `examples/qr-validos.txt`
y `examples/qr-invalidos.txt`.

---

## 4. Modelo canónico interno (`Transferencia`)

Después de parsear y validar, el mediador transforma la cadena TLV a este JSON
(los consumidores **solo** ven esta representación):

```json
{
  "id_transaccion": "TX000001",
  "payload_format_indicator": "01",
  "point_of_initiation_method": "11",
  "merchant_account_information": {
    "globally_unique_identifier": "py.gov.bcp.sip",
    "codigo_entidad": "0015",
    "numero_cuenta": "1234567890"
  },
  "merchant_category_code": "5731",
  "transaction_currency": "600",
  "transaction_amount": null,
  "country_code": "PY",
  "merchant_name": "COMERCIO DE PRUEBA",
  "merchant_city": "ASUNCION",
  "crc": "A1B2"
}
```

## 5. Resultado común

```json
{ "id_transaccion": "TX000001", "estado": "PROCESADA", "mensaje": "Transferencia procesada exitosamente en Banco Itaú Paraguay (cuenta 1234567890)" }
```

```json
{ "id_transaccion": "TX000002", "estado": "RECHAZADA", "mensaje": "Checksum inválido: '9999' (se esperaba A1B2)" }
```

---

## 6. Patrones EIP aplicados

| Patrón | Dónde se aplica |
|---|---|
| **Message Channel** | Canales `direct:sipap-in`, `direct:itau`, `direct:atlas`, `direct:familiar`, `direct:rechazo`, `direct:dead-letter`, `direct:resultado`. |
| **Pipes and Filters** | `MediadorRoute` separa recepción → parseo → validación → transformación → enrutamiento en rutas/canales independientes y encadenados. |
| **Message Translator** | `QrToTransferenciaTranslator`: convierte el mapa TLV en el modelo canónico `Transferencia`. |
| **Content-Based Router** | `direct:enrutamiento` usa `.choice()` sobre `codigo_entidad` para decidir el banco destino. |
| **Message Filter** | `QrValidationProcessor` actúa como compuerta: solo los mensajes que cumplen todas las reglas de negocio continúan hacia la transformación/enrutamiento. |
| **Correlation Identifier** | Header `idTransaccion` (`TXxxxxxx`), generado por el productor y propagado por todo el flujo hasta el resultado final. |
| **Dead Letter Channel** | `direct:dead-letter`: captura errores técnicos de parseo TLV (estructura corrupta, longitudes inválidas). |
| **Wire Tap** | `direct:audit`: audita cada mensaje recibido en crudo sin alterar el flujo principal (`wireTap()`). |

(Se implementan 8 de los 8 patrones sugeridos, superando el mínimo de 5 exigido.)

---

## 7. Requisitos

- Java 17+
- Maven 3.9+
- Conexión a internet la primera vez (para descargar las dependencias de Camel desde Maven Central)

## 8. Instalación y ejecución

```bash
# 1. Clonar el repositorio
git clone <URL-del-repo>
cd sipap-camel-integration

# 2. Compilar y correr las pruebas unitarias
mvn test

# 3a. Ejecutar la aplicación (opción recomendada)
mvn compile exec:java -Dexec.mainClass=com.bcp.sipap.MainApp

# 3b. Alternativa: usando el plugin de Camel
mvn camel:run

# 3c. Alternativa: generar un jar ejecutable
mvn package
java -jar target/sipap-camel-integration.jar
```

Al ejecutar, los dos productores comienzan a enviar cadenas QR (mezclando
casos válidos e inválidos) cada pocos segundos, y la consola mostrará el log
de cada etapa: recepción → auditoría → parseo → validación → transformación →
enrutamiento → resultado final (`PROCESADA` / `RECHAZADA`), cada uno
identificado con su `idTransaccion`.

Deténgase con `Ctrl+C`.

## 9. Ejecutar las pruebas unitarias e integración

```bash
mvn test
```

Incluye:
- `QrTlvParserTest`: parseo TLV válido, bloque anidado (tag 32), y errores
  estructurales (longitud incorrecta, cadena incompleta, tag duplicado).
- `QrValidatorTest`: los 7 escenarios mínimos exigidos por la consigna.
- `MediadorRouteTest`: prueba de integración extremo a extremo usando
  `camel-test-junit5` sobre las rutas reales (sin usar los productores/timers).

## 10. Escenarios de prueba cubiertos

| # | Escenario | Cubierto en |
|---|---|---|
| 1 | Transferencia válida → ITAU | `QrValidatorTest`, `MediadorRouteTest`, `examples/qr-validos.txt` |
| 2 | Transferencia válida → ATLAS | ídem |
| 3 | Transferencia válida → FAMILIAR | ídem |
| 4 | Banco destino desconocido | `QrValidatorTest#bancoDestinoDesconocidoEsRechazado`, `MediadorRouteTest`, `examples/qr-invalidos.txt` |
| 5 | Campo obligatorio ausente / longitud incorrecta | `QrValidatorTest#campoObligatorioAusenteEsRechazado`, `QrTlvParserTest#lanzaExcepcionSiLaLongitudDeclaradaExcedeLaCadena` |
| 6 | Monto ≥ 10.000.000 | `QrValidatorTest#montoMayorOIgualAlMaximoEsRechazado` |
| 7 | Checksum ≠ `A1B2` | `QrValidatorTest#checksumDistintoDeDummyEsRechazado`, `MediadorRouteTest` |

## 11. Diagrama del flujo

Ver `diagrams/flujo-eip.svg` (diagrama de referencia en notación EIP con el
flujo completo: productores → wire tap → parseo → validación → transformación
→ content-based router → consumidores/rechazo → resultado).

> Se recomienda recrear este diagrama en **draw.io** usando el stencil oficial
> de Enterprise Integration Patterns
> (https://drawio-app.com/blog/enterprise-integration-patterns-stencils/)
> para la entrega final, exportándolo como `diagrams/flujo-eip.drawio` / `.png`.

## 12. Evolución futura (fuera de alcance de esta práctica)

Los endpoints `direct:` podrán sustituirse en una etapa posterior por colas
de un broker (por ejemplo ActiveMQ Artemis) sin modificar el parser, los
validadores ni la lógica de negocio, ya que estos componentes no dependen del
tipo de canal usado.

## 13. Restricciones respetadas

- Solo Apache Camel (sin ActiveMQ/Artemis ni otro broker).
- Sin XML como formato de transferencia (TLV + JSON).
- Canal principal interno al proceso (`direct:`), síncrono.
- CRC dummy `A1B2`, sin implementar el algoritmo real de CRC.
- No hay conexión con bancos reales ni con la infraestructura real de SIPAP.
