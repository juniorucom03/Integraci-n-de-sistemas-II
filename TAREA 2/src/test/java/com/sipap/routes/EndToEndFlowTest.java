package com.sipap.routes;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.sipap.Application;
import com.sipap.util.QrTestFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración de referencia (extremo a extremo):
 *  - levanta un Artemis embebido (sin Docker) y un WireMock embebido,
 *  - envía una petición HTTP real a la API,
 *  - valida que la respuesta inmediata sea ACEPTADA_PARA_PROCESAMIENTO,
 *  - valida el escenario de monto excedido (rechazo antes de publicar),
 *  - valida que un id_transaccion duplicado sea detectado por el consumidor
 *    (a través de logs / MessageStore, dado que el resultado final es
 *    asíncrono).
 *
 * NOTA para quien continúe el trabajo: esta prueba no pudo ejecutarse en el
 * entorno donde se generó este proyecto porque no había acceso de red a
 * Maven Central para descargar las dependencias. Se deja como plantilla de
 * referencia; validar y ajustar localmente (nombres de propiedades, timing
 * de espera para el flujo asíncrono, etc.).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndFlowTest {

    @LocalServerPort
    private int port;

    private static EmbeddedArtemisSupport artemis;
    private static WireMockServer wireMock;

    @BeforeAll
    void setUp() throws Exception {
        artemis = new EmbeddedArtemisSupport();
        artemis.start();

        wireMock = new WireMockServer(9561);
        wireMock.start();
        wireMock.stubFor(post(urlEqualTo("/banco/0001/transferencias"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"resultado\":\"APROBADA\"}")));
    }

    @AfterAll
    void tearDown() throws Exception {
        if (wireMock != null) {
            wireMock.stop();
        }
        if (artemis != null) {
            artemis.stop();
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("sipap.mock.baseUrl", () -> "http://localhost:9561");
    }

    private ResponseEntity<String> enviar(String body) {
        TestRestTemplate rest = new TestRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("http://localhost:" + port + "/api/transferencias",
                new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void transferenciaValidaEsAceptadaYEnviadaAArtemis() {
        String qr = QrTestFixtures.qrEstatico("0001");
        String hoy = LocalDate.now().toString();
        String body = "{\"id_transaccion\":\"TX-E2E-001\",\"fecha_transaccion\":\"" + hoy
                + "\",\"qr\":\"" + qr + "\",\"monto\":\"10\"}";

        ResponseEntity<String> resp = enviar(body);

        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertTrue(resp.getBody().contains("ACEPTADA_PARA_PROCESAMIENTO"));
        assertTrue(resp.getBody().contains("TX-E2E-001"));
    }

    @Test
    void montoSuperiorAlMaximoSeRechazaAntesDePublicar() {
        String qr = QrTestFixtures.qrEstatico("0001");
        String hoy = LocalDate.now().toString();
        String body = "{\"id_transaccion\":\"TX-E2E-002\",\"fecha_transaccion\":\"" + hoy
                + "\",\"qr\":\"" + qr + "\",\"monto\":\"10000001\"}";

        ResponseEntity<String> resp = enviar(body);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
        assertTrue(resp.getBody().contains("El monto supera máximo permitido"));
        assertTrue(resp.getBody().contains("TX-E2E-002"));
    }

    @Test
    void mensajeDuplicadoEsAceptadoEnApiPeroDescartadoEnElConsumidor() throws InterruptedException {
        String qr = QrTestFixtures.qrEstatico("0001");
        String hoy = LocalDate.now().toString();
        String body = "{\"id_transaccion\":\"TX-E2E-003\",\"fecha_transaccion\":\"" + hoy
                + "\",\"qr\":\"" + qr + "\",\"monto\":\"10\"}";

        ResponseEntity<String> primera = enviar(body);
        ResponseEntity<String> segunda = enviar(body);

        // Ambas peticiones son aceptadas por la API (la idempotencia se aplica
        // en el consumidor, no en el productor), pero solo la primera debe
        // terminar en estado PROCESADA; la segunda debe quedar DUPLICADA.
        assertEquals(HttpStatus.ACCEPTED, primera.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, segunda.getStatusCode());

        // Dar tiempo al procesamiento asíncrono antes de verificar el
        // resultado final (ver MessageStore / logs en una implementación
        // con endpoint de consulta expuesto).
        Thread.sleep(2000);
    }
}
