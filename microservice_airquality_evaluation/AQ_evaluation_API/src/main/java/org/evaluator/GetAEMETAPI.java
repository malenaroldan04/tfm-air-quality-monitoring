package org.evaluator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.core.json.JsonObject;

public class GetAEMETAPI extends AbstractVerticle{

    private static final String API_KEY = System.getenv("API_KEY_AEMET");
    private static final String ID_ESTACION = "3191E"; //Colmenar Viejo

    @Override
    public void start(Promise<Void> startPromise){
        Vertx rxVertx = Vertx.newInstance(vertx);
        WebClient client = WebClient.create(rxVertx);

        String endpoint = "https://opendata.aemet.es/opendata/api/observacion/convencional/datos/estacion/" + ID_ESTACION;

        client
                .getAbs(endpoint)
                .putHeader("accept", "application/json")
                .putHeader("api_key", API_KEY)
                .rxSend()
                .flatMap(response -> {
                    JsonObject body = response.bodyAsJsonObject();
                    System.out.println("Respuesta AEMET (metadata): " + body);

                    String dataUrl = body.getString("datos");
                    if(dataUrl == null){
                        return io.reactivex.rxjava3.core.Single.error(new RuntimeException("No se encontró la URL de datos en la respuesta"));
                    }

                    return client.getAbs(dataUrl).rxSend();
                })
                .subscribe(
                        finalResponse -> {
                            System.out.println("Datos de observación:");
                            System.out.println(finalResponse.bodyAsString());
                            startPromise.complete();
                        },
                        err -> {
                            System.err.println("Error al hacer la solicitud a AEMET:");
                            err.printStackTrace();
                            startPromise.fail(err);
                        }
                );
    }
}
