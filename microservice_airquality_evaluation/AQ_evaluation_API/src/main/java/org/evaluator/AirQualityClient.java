package org.evaluator;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.core.json.JsonObject;

public class AirQualityClient {

    //Para air-quality API
    public static Single<JsonObject> fetchAirQuality(Vertx vertx, String locationId, String time){
        WebClient client = WebClient.create(vertx);
        String url = "https://air-quality-api-486709162249.us-central1.run.app";

        return client
                .getAbs(url + "/air-quality")
                .addQueryParam("locationId", locationId)
                .addQueryParam("time", time)
                .rxSend()
                .map(response -> response.bodyAsJsonObject());
    }

    //Para AEMET API
    public static Single<JsonObject> fetchAemetData(Vertx vertx, String stationId, String apiKey){
        WebClient client = WebClient.create(vertx);
        String endpoint = "https://opendata.aemet.es/opendata/api/observacion/convencional/datos/estacion/" + stationId;

        return client
                .getAbs(endpoint)
                .putHeader("accept", "application/json")
                .putHeader("api_key", apiKey)
                .rxSend()
                .flatMap(response -> {
                    JsonObject body = response.bodyAsJsonObject();
                    String dataUrl = body.getString("datos");

                    if(dataUrl == null){
                        return Single.error(new RuntimeException("No se encontró la URL de datos en la respuesta"));
                    }

                    return client.getAbs(dataUrl).rxSend().map(finalResponse -> {
                        String rawJson = finalResponse.bodyAsString();
                        JsonArray jsonArray = new JsonArray(rawJson);
                        return new JsonObject().put("data", jsonArray);
                    });
                });
    }
}
