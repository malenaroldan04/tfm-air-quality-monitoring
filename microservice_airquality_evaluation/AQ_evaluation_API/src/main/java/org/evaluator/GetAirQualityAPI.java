package org.evaluator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;

public class GetAirQualityAPI extends AbstractVerticle{

    @Override
    public void start(Promise<Void> startPromise){
        Vertx rxVertx = Vertx.newInstance(vertx);
        WebClient client = WebClient.create(rxVertx);

        String url = "https://air-quality-api-486709162249.us-central1.run.app";
        String locationId = "testzone";
        String time = "2025-05-13T10:33";

        client
                .getAbs(url + "/air-quality")
                .addQueryParam("locationId", locationId)
                .addQueryParam("time", time)
                .rxSend()
                .subscribe(
                        response -> {
                            System.out.println("Respuesta del microservicio:");
                            System.out.println(response.bodyAsString());
                            startPromise.complete();
                        },
                        err ->{
                            System.err.println("Error al hacer la solicitud:");
                            err.printStackTrace();
                            startPromise.fail(err);
                        }
                );
    }
}