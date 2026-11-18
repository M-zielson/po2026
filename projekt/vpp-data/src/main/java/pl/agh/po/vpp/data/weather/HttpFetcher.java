package pl.agh.po.vpp.data.weather;

import java.io.IOException;
import java.net.URI;

/**
 * Pobranie odpowiedzi HTTP (GET) jako tekstu. Osobny interfejs, żeby testy mogły podstawić
 * zapisane odpowiedzi Open-Meteo zamiast łączyć się z internetem.
 */
@FunctionalInterface
public interface HttpFetcher {

    /**
     * @return kod statusu i treść odpowiedzi – także dla błędów HTTP (4xx/5xx), bo Open-Meteo
     *         opisuje w treści, co było nie tak
     * @throws IOException gdy w ogóle nie udało się połączyć albo minął limit czasu
     */
    HttpResult get(URI uri) throws IOException;

    /** Odpowiedź HTTP: kod statusu i treść. */
    record HttpResult(int statusCode, String body) {

        public HttpResult {
            if (body == null) {
                body = "";
            }
        }

        public boolean isOk() {
            return statusCode == 200;
        }
    }
}
